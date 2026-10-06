package com.tickethub.payment.support;

import com.tickethub.payment.exception.PaymentProviderException;
import com.tickethub.payment.gateway.AmountUtil;
import com.tickethub.payment.gateway.GatewayOrder;
import com.tickethub.payment.gateway.GatewayPayment;
import com.tickethub.payment.gateway.GatewayRefund;
import com.tickethub.payment.gateway.HmacSignatures;
import com.tickethub.payment.gateway.PaymentGateway;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scriptable stand-in for Razorpay. Tests tell it what "Razorpay" knows (payments, attempts on an order)
 * and flip switches to simulate outages; it counts the calls so tests can assert "no second order was created".
 */
public class FakeGateway implements PaymentGateway {

    public static final String KEY_SECRET = "test_key_secret";
    public static final String WEBHOOK_SECRET = "test_webhook_secret";

    public int ordersCreated = 0;
    public int fetchCalls = 0;
    public int refundsRequested = 0;
    public boolean failOrderCreation = false;
    public boolean refundThrows = false;

    /** What Razorpay would answer for fetchPayment(id). */
    public final Map<String, GatewayPayment> payments = new HashMap<>();
    /** What Razorpay would answer for fetchOrderPayments(orderId). */
    public final Map<String, List<GatewayPayment>> orderPayments = new HashMap<>();

    @Override
    public String providerName() {
        return "FAKE";
    }

    @Override
    public String publicKey() {
        return "rzp_test_fake";
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes) {
        if (failOrderCreation) {
            throw new PaymentProviderException("Razorpay is down (simulated)");
        }
        ordersCreated++;
        return new GatewayOrder("order_" + ordersCreated, AmountUtil.toMinorUnits(amount), currency, "created");
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return HmacSignatures.matches(HmacSignatures.hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET), signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        return HmacSignatures.matches(HmacSignatures.hmacSha256Hex(rawBody, WEBHOOK_SECRET), signature);
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        fetchCalls++;
        GatewayPayment p = payments.get(paymentId);
        if (p == null) {
            throw new PaymentProviderException("No such payment at Razorpay: " + paymentId);
        }
        return p;
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String orderId) {
        return orderPayments.getOrDefault(orderId, new ArrayList<>());
    }

    @Override
    public GatewayPayment capture(String paymentId, BigDecimal amount, String currency) {
        GatewayPayment authorized = fetchPayment(paymentId);
        GatewayPayment captured = payment(authorized.id(), authorized.orderId(), "captured", authorized.amountInMinorUnits());
        payments.put(paymentId, captured);
        return captured;
    }

    @Override
    public GatewayRefund refund(String paymentId, BigDecimal amount, String receipt) {
        refundsRequested++;
        if (refundThrows) {
            throw new PaymentProviderException("refund rejected (simulated)");
        }
        return new GatewayRefund("rfnd_" + refundsRequested, paymentId, AmountUtil.toMinorUnits(amount), "processed");
    }

    // ---------------------------------------------------------------- helpers for tests

    /** The proof the browser would send after paying. */
    public static String signCheckout(String orderId, String paymentId) {
        return HmacSignatures.hmacSha256Hex(orderId + "|" + paymentId, KEY_SECRET);
    }

    /** The X-Razorpay-Signature Razorpay would send for this webhook body. */
    public static String signWebhook(String body) {
        return HmacSignatures.hmacSha256Hex(body, WEBHOOK_SECRET);
    }

    public static GatewayPayment payment(String id, String orderId, String status, long amountPaise) {
        return new GatewayPayment(id, orderId, status, "upi", amountPaise, "INR",
                "failed".equals(status) ? "Payment failed (simulated)" : null, 0, null, 1_000);
    }

    /** Registers a payment so that fetchPayment(id) finds it. */
    public GatewayPayment givenPayment(String id, String orderId, String status, long amountPaise) {
        GatewayPayment p = payment(id, orderId, status, amountPaise);
        payments.put(id, p);
        return p;
    }
}
