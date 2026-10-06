package com.tickethub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.config.PaymentProperties;
import com.tickethub.payment.dto.PaymentEvent;
import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.dto.VerifyPaymentRequest;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.entity.WebhookEvent;
import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.kafka.PaymentEventPublisher;
import com.tickethub.payment.repository.WebhookEventRepository;
import com.tickethub.payment.support.FakeGateway;
import com.tickethub.payment.support.FakePaymentDb;
import com.tickethub.payment.support.TestTx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Webhook scenarios from the blueprint: SC-10 (same webhook twice) and SC-11 (webhook arrives late / out of order),
 * plus the security basics (forged calls) and "events that are not ours".
 */
class PaymentWebhookServiceTest {

    private FakePaymentDb db;
    private FakeGateway gateway;
    private PaymentEventPublisher publisher;
    private PaymentService paymentService;
    private PaymentWebhookService webhook;
    private final Set<String> receipts = new HashSet<>();

    private String orderId;
    private long paymentId;

    @BeforeEach
    void setUp() {
        db = new FakePaymentDb();
        gateway = new FakeGateway();
        publisher = mock(PaymentEventPublisher.class);
        paymentService = new PaymentService(db.repository, gateway, publisher, TestTx.noOp(), new PaymentProperties());

        WebhookEventRepository receiptRepo = mock(WebhookEventRepository.class);
        when(receiptRepo.existsByEventId(anyString())).thenAnswer(inv -> receipts.contains((String) inv.getArgument(0)));
        when(receiptRepo.saveAndFlush(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            receipts.add(e.getEventId());
            return e;
        });
        webhook = new PaymentWebhookService(gateway, paymentService, receiptRepo, TestTx.noOp(), new ObjectMapper());

        PaymentResponse created = paymentService.createPayment(
                new PaymentRequest(101L, 7L, "asha@example.com", new BigDecimal("500.00"), "INR", "k1"));
        orderId = created.providerOrderId();
        paymentId = created.paymentId();
    }

    // ------------------------------------------------------------------ payload builders (shaped like Razorpay's real payloads)

    private String paymentEvent(String event, String payId, String status, long amountPaise, String order) {
        return "{\"entity\":\"event\",\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + payId + "\",\"entity\":\"payment\",\"amount\":" + amountPaise + ",\"currency\":\"INR\","
                + "\"status\":\"" + status + "\",\"order_id\":" + (order == null ? "null" : "\"" + order + "\"") + ","
                + "\"method\":\"upi\",\"error_description\":" + ("failed".equals(status) ? "\"Payment failed\"" : "null")
                + ",\"created_at\":1700000000}}}}";
    }

    private String refundEvent(String event, String refundId, String payId) {
        return "{\"entity\":\"event\",\"event\":\"" + event + "\",\"payload\":{\"refund\":{\"entity\":{"
                + "\"id\":\"" + refundId + "\",\"entity\":\"refund\",\"payment_id\":\"" + payId + "\",\"amount\":50000}}}}";
    }

    private String deliver(String body, String eventId) {
        return webhook.handle(body.getBytes(StandardCharsets.UTF_8), FakeGateway.signWebhook(body), eventId);
    }

    private List<PaymentEvent> events() {
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(publisher, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------ security

    @Test
    void forgedSignature_isRejectedAndNothingIsRecorded() {
        String body = paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId);

        assertThrows(InvalidSignatureException.class,
                () -> webhook.handle(body.getBytes(StandardCharsets.UTF_8), "not-the-real-signature", "evt_1"));

        assertEquals(PaymentStatus.PENDING, db.row(paymentId).getStatus());
        assertTrue(receipts.isEmpty());
        assertTrue(events().isEmpty());
    }

    @Test
    void missingSignatureHeader_isRejected() {
        String body = paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId);

        assertThrows(InvalidSignatureException.class,
                () -> webhook.handle(body.getBytes(StandardCharsets.UTF_8), null, "evt_1"));
    }

    @Test
    void signatureIsOverTheExactBytes_aSingleChangedByteBreaksIt() {
        String body = paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId);
        String signatureOfOriginal = FakeGateway.signWebhook(body);
        String tampered = body.replace("50000", "10000");

        assertThrows(InvalidSignatureException.class,
                () -> webhook.handle(tampered.getBytes(StandardCharsets.UTF_8), signatureOfOriginal, "evt_1"));
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void captured_marksSuccessAndPublishesOneEvent() {
        String result = deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");

        assertEquals("processed", result);
        assertEquals(PaymentStatus.SUCCESS, db.row(paymentId).getStatus());
        assertEquals("pay_1", db.row(paymentId).getProviderPaymentId());
        assertEquals(1, events().size());
        assertEquals("PAYMENT_SUCCESS", events().get(0).eventType());
    }

    // ------------------------------------------------------------------ SC-10: duplicates

    @Test
    void sameEventDeliveredTwice_isProcessedOnce() {
        String body = paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId);

        assertEquals("processed", deliver(body, "evt_1"));
        assertEquals("duplicate", deliver(body, "evt_1"));

        assertEquals(1, events().size());
    }

    @Test
    void capturedAndOrderPaid_forTheSamePayment_publishOnlyOneSuccess() {
        // Razorpay really sends BOTH events (different event ids) for one successful payment
        deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");
        deliver(paymentEvent("order.paid", "pay_1", "captured", 50000, orderId), "evt_2");

        assertEquals(1, events().size());
        assertEquals(PaymentStatus.SUCCESS, db.row(paymentId).getStatus());
    }

    @Test
    void webhookThenBrowserVerify_publishOnlyOneSuccess() {
        // The customer's browser verify and Razorpay's webhook race; whoever is second must change nothing
        deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");
        gateway.givenPayment("pay_1", orderId, "captured", 50000);
        paymentService.verifyPayment(new VerifyPaymentRequest(orderId, "pay_1", FakeGateway.signCheckout(orderId, "pay_1")));

        assertEquals(1, events().size());
    }

    // ------------------------------------------------------------------ SC-11: disorder

    @Test
    void failedAttemptThenCapturedRetry_endsAsSuccess() {
        deliver(paymentEvent("payment.failed", "pay_bad", "failed", 50000, orderId), "evt_1");
        assertEquals(PaymentStatus.FAILED, db.row(paymentId).getStatus());

        deliver(paymentEvent("payment.captured", "pay_good", "captured", 50000, orderId), "evt_2");

        assertEquals(PaymentStatus.SUCCESS, db.row(paymentId).getStatus());
        assertEquals("pay_good", db.row(paymentId).getProviderPaymentId());
    }

    @Test
    void lateFailedEventAfterSuccess_isIgnored() {
        deliver(paymentEvent("payment.captured", "pay_good", "captured", 50000, orderId), "evt_1");
        deliver(paymentEvent("payment.failed", "pay_bad", "failed", 50000, orderId), "evt_2");

        assertEquals(PaymentStatus.SUCCESS, db.row(paymentId).getStatus());
        assertEquals("pay_good", db.row(paymentId).getProviderPaymentId());
        assertEquals(1, events().size(), "no 'payment failed' mail for a booking that is paid");
    }

    // ------------------------------------------------------------------ events that are not for us / not interesting

    @Test
    void eventForUnknownOrder_isAcknowledgedAndIgnored() {
        String result = deliver(paymentEvent("payment.captured", "pay_x", "captured", 50000, "order_not_ours"), "evt_1");

        assertEquals("ignored", result);
        assertTrue(events().isEmpty());
    }

    @Test
    void paymentWithoutOrder_isIgnored() {
        assertEquals("ignored", deliver(paymentEvent("payment.captured", "pay_x", "captured", 50000, null), "evt_1"));
    }

    @Test
    void uninterestingEventType_isAcknowledgedButChangesNothing() {
        String result = deliver(paymentEvent("payment.authorized", "pay_1", "authorized", 50000, orderId), "evt_1");

        assertEquals("ignored", result);
        assertEquals(PaymentStatus.PENDING, db.row(paymentId).getStatus());
        assertTrue(receipts.contains("evt_1"), "still recorded, so a duplicate is recognised later");
    }

    @Test
    void capturedWithWrongAmount_isNotAccepted() {
        String result = deliver(paymentEvent("payment.captured", "pay_1", "captured", 100, orderId), "evt_1");

        assertEquals("ignored", result);
        assertEquals(PaymentStatus.PENDING, db.row(paymentId).getStatus());
        assertTrue(events().isEmpty());
    }

    @Test
    void missingEventIdHeader_fallsBackToHashOfBody_soDuplicatesAreStillCaught() {
        String body = paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId);

        assertEquals("processed", deliver(body, null));
        assertEquals("duplicate", deliver(body, null));
    }

    // ------------------------------------------------------------------ refunds started in the Razorpay Dashboard

    @Test
    void refundProcessed_fromDashboard_marksRefundedAndPublishes() {
        deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");

        deliver(refundEvent("refund.processed", "rfnd_9", "pay_1"), "evt_2");

        assertEquals(PaymentStatus.REFUNDED, db.row(paymentId).getStatus());
        assertEquals("rfnd_9", db.row(paymentId).getProviderRefundId());
        assertEquals("PAYMENT_REFUNDED", events().get(1).eventType());
    }

    @Test
    void refundProcessed_forRefundWeStartedOurselves_changesNothingTwice() {
        deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");
        paymentService.refundPayment(paymentId);   // our own refund -> REFUNDED + 1 event
        int before = events().size();

        deliver(refundEvent("refund.processed", "rfnd_1", "pay_1"), "evt_2");

        assertEquals(before, events().size());
        assertEquals(PaymentStatus.REFUNDED, db.row(paymentId).getStatus());
    }

    @Test
    void refundFailed_isLoggedForHumans_butStatusIsKept() {
        deliver(paymentEvent("payment.captured", "pay_1", "captured", 50000, orderId), "evt_1");

        deliver(refundEvent("refund.failed", "rfnd_9", "pay_1"), "evt_2");

        assertEquals(PaymentStatus.SUCCESS, db.row(paymentId).getStatus());
        assertTrue(db.row(paymentId).getFailureReason().contains("Refund failed"));
    }

    @Test
    void malformedJson_withValidSignature_isRejected() {
        String body = "{ this is not json";
        assertThrows(com.tickethub.payment.exception.InvalidPaymentRequestException.class,
                () -> webhook.handle(body.getBytes(StandardCharsets.UTF_8), FakeGateway.signWebhook(body), "evt_1"));
    }
}
