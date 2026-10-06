package com.tickethub.payment.exception;

/**
 * Thrown when a signature does not match - either a checkout result or a webhook call.
 * It means "this message was NOT proven to come from Razorpay", so we change nothing and answer 400.
 */
public class InvalidSignatureException extends RuntimeException {
    public InvalidSignatureException(String message) {
        super(message);
    }
}
