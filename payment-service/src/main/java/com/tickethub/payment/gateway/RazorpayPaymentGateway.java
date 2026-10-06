package com.tickethub.payment.gateway;

import com.razorpay.Entity;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.tickethub.payment.config.PaymentProperties;
import com.tickethub.payment.exception.PaymentProviderException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The REAL Razorpay integration. This is the only class in the project that knows Razorpay's SDK or JSON.
 *
 * Active when tickethub.payment.gateway=razorpay (the default).
 * SDK reference: https://github.com/razorpay/razorpay-java
 */
@Component
@ConditionalOnProperty(name = "tickethub.payment.gateway", havingValue = "razorpay", matchIfMissing = true)
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayClient client;
    private final PaymentProperties.Razorpay config;

    public RazorpayPaymentGateway(RazorpayClient client, PaymentProperties properties) {
        this.client = client;
        this.config = properties.getRazorpay();
    }

    @Override
    public String providerName() {
        return "RAZORPAY";
    }

    @Override
    public String publicKey() {
        return config.getKeyId();
    }

    // ---------------------------------------------------------------- step 1: create the order

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", AmountUtil.toMinorUnits(amount));   // paise, a whole number
            request.put("currency", currency);
            request.put("receipt", receipt);
            JSONObject noteJson = new JSONObject();
            notes.forEach((k, v) -> noteJson.put(k, v));
            request.put("notes", noteJson);

            Order order = client.orders.create(request);
            JSONObject json = order.toJson();
            log.info("Razorpay order {} created for receipt {}", json.getString("id"), receipt);
            return new GatewayOrder(json.getString("id"), json.getLong("amount"),
                    json.getString("currency"), json.optString("status", "created"));
        } catch (RazorpayException e) {
            throw new PaymentProviderException("Razorpay could not create the order: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- step 3/4: verify proofs (local, no network)

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        // Razorpay's documented rule: HMAC_SHA256(order_id + "|" + razorpay_payment_id, key_secret)
        String expected = HmacSignatures.hmacSha256Hex(orderId + "|" + paymentId, config.getKeySecret());
        return HmacSignatures.matches(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        // Razorpay's documented rule: HMAC_SHA256(RAW request body, webhook_secret)
        String expected = HmacSignatures.hmacSha256Hex(rawBody, config.getWebhookSecret());
        return HmacSignatures.matches(expected, signature);
    }

    // ---------------------------------------------------------------- ask Razorpay for the truth

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        try {
            return toGatewayPayment(client.payments.fetch(paymentId));
        } catch (RazorpayException e) {
            throw new PaymentProviderException("Razorpay could not fetch payment " + paymentId + ": " + e.getMessage());
        }
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String orderId) {
        try {
            List<GatewayPayment> result = new ArrayList<>();
            for (Payment p : client.orders.fetchPayments(orderId)) {
                result.add(toGatewayPayment(p));
            }
            return result;
        } catch (RazorpayException e) {
            throw new PaymentProviderException("Razorpay could not list payments of order " + orderId + ": " + e.getMessage());
        }
    }

    @Override
    public GatewayPayment capture(String paymentId, BigDecimal amount, String currency) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", AmountUtil.toMinorUnits(amount));   // must equal the authorized amount
            request.put("currency", currency);
            return toGatewayPayment(client.payments.capture(paymentId, request));
        } catch (RazorpayException e) {
            throw new PaymentProviderException("Razorpay could not capture payment " + paymentId + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- refund

    @Override
    public GatewayRefund refund(String paymentId, BigDecimal amount, String receipt) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", AmountUtil.toMinorUnits(amount));
            request.put("speed", "normal");
            request.put("receipt", receipt);   // Razorpay rejects a second refund request with the same receipt

            // Declared as the common base type "Entity" so this compiles whichever concrete type the SDK version returns.
            Entity refund = client.payments.refund(paymentId, request);
            JSONObject json = refund.toJson();
            log.info("Razorpay refund {} requested for payment {}", json.optString("id"), paymentId);
            return new GatewayRefund(json.optString("id", null), paymentId,
                    json.optLong("amount", AmountUtil.toMinorUnits(amount)), json.optString("status", "pending"));
        } catch (RazorpayException e) {
            throw new PaymentProviderException("Razorpay refund failed for payment " + paymentId + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- translation Razorpay JSON -> our type

    private static GatewayPayment toGatewayPayment(Entity payment) {
        JSONObject j = payment.toJson();
        return new GatewayPayment(
                j.optString("id", null),
                nullSafe(j, "order_id"),
                j.optString("status", ""),
                nullSafe(j, "method"),
                j.optLong("amount", 0),
                j.optString("currency", ""),
                nullSafe(j, "error_description"),
                j.optLong("amount_refunded", 0),
                nullSafe(j, "refund_status"),
                j.optLong("created_at", 0));
    }

    /** Razorpay sends JSON null for empty fields; org.json would turn that into the TEXT "null". We want a real null. */
    private static String nullSafe(JSONObject json, String key) {
        return json.isNull(key) ? null : json.optString(key, null);
    }
}
