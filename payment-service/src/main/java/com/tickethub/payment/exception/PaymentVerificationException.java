package com.tickethub.payment.exception;

/**
 * Thrown when the signature is fine but Razorpay's own record disagrees with ours
 * (different order, different amount, different currency). Should never happen in normal use,
 * so it is logged loudly and answered with 409 - and the payment is NOT marked successful.
 */
public class PaymentVerificationException extends RuntimeException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}
