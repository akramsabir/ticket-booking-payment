package com.tickethub.payment.gateway;

import com.tickethub.payment.exception.PaymentProviderException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A FAKE provider so you can run the service without a Razorpay account (tickethub.payment.gateway=mock).
 * It follows the SAME flow as Razorpay (order -> signature -> fetch), so the rest of the code is exercised for real.
 *
 * How to "pay" against the mock:
 *   1. POST /api/v1/payments                     -> note the providerOrderId, e.g. order_mock_ab12cd34ef56
 *   2. The mock payment id is the order id with "order_" replaced by "pay_":  pay_mock_ab12cd34ef56
 *   3. Sign it:  echo -n "order_mock_ab12cd34ef56|pay_mock_ab12cd34ef56" | openssl dgst -sha256 -hmac mock_secret
 *   4. POST /api/v1/payments/verify with those three values.
 */
@Component
@ConditionalOnProperty(name = "tickethub.payment.gateway", havingValue = "mock")
public class MockPaymentGateway implements PaymentGateway {

    /** The shared secret of the fake provider. Fine to be public: it protects nothing real. */
    public static final String MOCK_SECRET = "mock_secret";

    private final Map<String, Long> orderAmounts = new ConcurrentHashMap<>();

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public String publicKey() {
        return "rzp_test_mock";
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        String orderId = "order_mock_" + shortId();
        long minor = AmountUtil.toMinorUnits(amount);
        orderAmounts.put(orderId, minor);
        return new GatewayOrder(orderId, minor, currency, "created");
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return HmacSignatures.matches(HmacSignatures.hmacSha256Hex(orderId + "|" + paymentId, MOCK_SECRET), signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        return HmacSignatures.matches(HmacSignatures.hmacSha256Hex(rawBody, MOCK_SECRET), signature);
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        if (paymentId == null || !paymentId.startsWith("pay_mock_")) {
            throw new PaymentProviderException("Unknown mock payment id: " + paymentId);
        }
        String orderId = "order_" + paymentId.substring("pay_".length());
        long amount = orderAmounts.getOrDefault(orderId, 0L);
        return new GatewayPayment(paymentId, orderId, "captured", "upi", amount, "INR", null, 0, null, 0);
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String orderId) {
        return List.of();   // nobody has "paid" outside of an explicit verify call
    }

    @Override
    public GatewayPayment capture(String paymentId, BigDecimal amount, String currency) {
        return fetchPayment(paymentId);
    }

    @Override
    public GatewayRefund refund(String paymentId, BigDecimal amount, String receipt) {
        return new GatewayRefund("rfnd_mock_" + shortId(), paymentId, AmountUtil.toMinorUnits(amount), "processed");
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
