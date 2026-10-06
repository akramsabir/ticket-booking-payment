package com.tickethub.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.WebhookEvent;
import com.tickethub.payment.exception.InvalidPaymentRequestException;
import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.exception.PaymentVerificationException;
import com.tickethub.payment.gateway.GatewayPayment;
import com.tickethub.payment.gateway.PaymentGateway;
import com.tickethub.payment.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Handles the calls Razorpay makes TO US ("webhooks"). This is how we learn a payment result even when the
 * customer closed the browser tab before our verify call.
 *
 * The four rules every webhook handler must follow (all from Razorpay's docs and the TicketHub blueprint):
 *   1. VERIFY the signature on the RAW body first. Anyone can POST to a public URL; only Razorpay knows the secret.
 *   2. Be IDEMPOTENT (SC-10): Razorpay can send the same event twice. We keep a receipt per X-Razorpay-Event-Id.
 *   3. Expect DISORDER (SC-11): a "failed" can arrive after a "captured". The shared state machine in PaymentService handles it.
 *   4. Answer 2xx only when we are truly done. If we fail, we answer non-2xx and Razorpay retries (for up to 24h).
 *
 * Events we act on:   payment.captured, order.paid, payment.failed, refund.processed, refund.failed
 * Everything else (payment.authorized, refund.created ...) is acknowledged and ignored.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;
    private final WebhookEventRepository webhookEventRepository;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public PaymentWebhookService(PaymentGateway paymentGateway,
                                 PaymentService paymentService,
                                 WebhookEventRepository webhookEventRepository,
                                 TransactionTemplate transactionTemplate,
                                 ObjectMapper objectMapper) {
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.webhookEventRepository = webhookEventRepository;
        this.tx = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** What happened to one delivery: handed back to the controller as a tiny status word. */
    private record Result(String status, PaymentService.Outcome outcome) {
    }

    /**
     * @param rawBody   the request body EXACTLY as received (the signature is computed over these bytes)
     * @param signature value of the X-Razorpay-Signature header
     * @param eventId   value of the X-Razorpay-Event-Id header (may be missing in hand-made test calls)
     * @return "processed", "duplicate" or "ignored"
     */
    public String handle(byte[] rawBody, String signature, String eventId) {
        // Rule 1: signature, before we even look at the content
        if (signature == null || signature.isBlank() || !paymentGateway.verifyWebhookSignature(rawBody, signature)) {
            throw new InvalidSignatureException("webhook signature mismatch");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new InvalidPaymentRequestException("Webhook body is not valid JSON");
        }
        String eventType = root.path("event").asText("");
        String dedupeId = (eventId == null || eventId.isBlank()) ? "sha256:" + sha256Hex(rawBody) : eventId;

        // Rule 2 + 4: receipt and business change in ONE transaction. If anything below throws, the receipt is rolled
        // back too, we answer non-2xx, and Razorpay's retry gets processed from scratch.
        Result result = tx.execute(status -> {
            if (webhookEventRepository.existsByEventId(dedupeId)) {
                return new Result("duplicate", null);
            }
            webhookEventRepository.saveAndFlush(new WebhookEvent(dedupeId, eventType));
            PaymentService.Outcome outcome = dispatch(eventType, root);
            return new Result(outcome == null ? "ignored" : "processed", outcome);
        });

        log.info("Webhook {} (id {}) -> {}", eventType, dedupeId, result.status());

        // After the commit: Kafka event and/or automatic refund
        if (result.outcome() != null) {
            paymentService.finish(result.outcome());
        }
        return result.status();
    }

    private PaymentService.Outcome dispatch(String eventType, JsonNode root) {
        switch (eventType) {
            case "payment.captured", "order.paid", "payment.failed":
                return handlePaymentEvent(eventType, root.path("payload").path("payment").path("entity"));
            case "refund.processed", "refund.failed":
                return handleRefundEvent(eventType, root.path("payload").path("refund").path("entity"));
            default:
                log.debug("Ignoring webhook event type '{}'", eventType);
                return null;
        }
    }

    private PaymentService.Outcome handlePaymentEvent(String eventType, JsonNode entity) {
        if (entity.isMissingNode() || entity.isNull()) {
            log.warn("Webhook {} has no payment entity; ignoring", eventType);
            return null;
        }
        GatewayPayment provider = toGatewayPayment(entity);
        if (provider.orderId() == null) {
            log.info("Webhook {} for payment {} has no order id (not created by us); ignoring", eventType, provider.id());
            return null;
        }
        Optional<Payment> payment = paymentService.lockByProviderOrderId(provider.orderId());
        if (payment.isEmpty()) {
            // Same Razorpay account may serve other systems; acknowledging stops Razorpay from retrying forever.
            log.warn("Webhook {} refers to unknown order {}; ignoring", eventType, provider.orderId());
            return null;
        }
        try {
            return paymentService.applyProviderPayment(payment.get(), provider);
        } catch (PaymentVerificationException e) {
            // Retrying cannot fix a mismatch, so we acknowledge (and the error is already logged loudly).
            log.error("Webhook {} rejected: {}", eventType, e.getMessage());
            return null;
        }
    }

    private PaymentService.Outcome handleRefundEvent(String eventType, JsonNode entity) {
        if (entity.isMissingNode() || entity.isNull()) {
            log.warn("Webhook {} has no refund entity; ignoring", eventType);
            return null;
        }
        String refundId = text(entity, "id");
        String paymentId = text(entity, "payment_id");
        if (paymentId == null) {
            return null;
        }
        Optional<Payment> payment = paymentService.lockByProviderPaymentId(paymentId);
        if (payment.isEmpty()) {
            log.warn("Webhook {} refers to unknown payment {}; ignoring", eventType, paymentId);
            return null;
        }
        String status = "refund.failed".equals(eventType) ? "failed" : "processed";
        return paymentService.applyProviderRefund(payment.get(), refundId, status);
    }

    // ------------------------------------------------------------------ helpers

    /** Translates the "payment.entity" JSON of a webhook into our own type. */
    private static GatewayPayment toGatewayPayment(JsonNode e) {
        return new GatewayPayment(
                text(e, "id"),
                text(e, "order_id"),
                e.path("status").asText(""),
                text(e, "method"),
                e.path("amount").asLong(0),
                e.path("currency").asText(""),
                text(e, "error_description"),
                e.path("amount_refunded").asLong(0),
                text(e, "refund_status"),
                e.path("created_at").asLong(0));
    }

    /** Returns null for missing OR JSON-null fields (Jackson's asText() would give the text "null"). */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
