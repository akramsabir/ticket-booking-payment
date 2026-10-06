package com.tickethub.payment.exception;

/** Thrown when an action is not allowed in the payment's current status (e.g. refunding a FAILED payment). Becomes HTTP 409. */
public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
