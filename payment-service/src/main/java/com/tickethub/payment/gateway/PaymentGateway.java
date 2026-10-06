package com.tickethub.payment.gateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The "door" to the external payment provider.
 * Our business code (PaymentService) only knows this interface. Today there are two implementations:
 *   RazorpayPaymentGateway - the real thing (tickethub.payment.gateway=razorpay, the default)
 *   MockPaymentGateway     - a fake for tests / working without keys (tickethub.payment.gateway=mock)
 *
 * The Razorpay flow in four steps, and which method serves each:
 *   1. SERVER creates an ORDER (amount fixed by us, never by the browser)      -> createOrder
 *   2. BROWSER opens Razorpay Checkout with that order id and the customer pays (no code of ours involved)
 *   3. SERVER checks the proof the browser brings back                         -> verifyPaymentSignature (+ fetchPayment)
 *   4. Razorpay ALSO calls our webhook, so we learn the result even if the browser tab was closed
 *                                                                              -> verifyWebhookSignature
 * Afterwards: refund(), and fetchOrderPayments() to reconcile when we missed a message.
 *
 * Every method throws PaymentProviderException when the provider itself fails (network, bad keys, rejected request).
 */
public interface PaymentGateway {

    /** "RAZORPAY" or "MOCK" - stored on the payment row. */
    String providerName();

    /** The PUBLIC key id the browser needs for Razorpay Checkout. (Not a secret.) */
    String publicKey();

    /**
     * Creates an order at the provider. receipt = our own reference (Razorpay allows max 40 characters),
     * notes = free-form tags visible in the Razorpay Dashboard (handy for support: bookingId, paymentId).
     */
    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt, Map<String, String> notes);

    /** Pure local check (no network): is this the signature Razorpay would produce for this order + payment? */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** Pure local check (no network): does this webhook body match the X-Razorpay-Signature header? Uses the RAW bytes. */
    boolean verifyWebhookSignature(byte[] rawBody, String signature);

    /** Asks the provider for the truth about one payment. */
    GatewayPayment fetchPayment(String paymentId);

    /** Asks the provider for ALL attempts made on one order (used by reconciliation). */
    List<GatewayPayment> fetchOrderPayments(String orderId);

    /** Moves an AUTHORIZED payment to CAPTURED. Only needed when the account uses manual capture. */
    GatewayPayment capture(String paymentId, BigDecimal amount, String currency);

    /**
     * Returns money to the customer. receipt doubles as Razorpay's duplicate guard: asking twice with the same
     * receipt is rejected, so a double-click can never refund twice.
     */
    GatewayRefund refund(String paymentId, BigDecimal amount, String receipt);
}
