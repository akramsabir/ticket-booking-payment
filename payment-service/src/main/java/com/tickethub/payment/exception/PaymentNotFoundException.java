package com.tickethub.payment.exception;

/** Thrown when someone asks for a payment that does not exist. Becomes HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment not found with id: " + paymentId);
    }

    private PaymentNotFoundException(String message) {
        super(message);
    }

    /** Used when Razorpay gives us an order id that we never created. */
    public static PaymentNotFoundException forOrder(String providerOrderId) {
        return new PaymentNotFoundException("Payment not found for order: " + providerOrderId);
    }
}
