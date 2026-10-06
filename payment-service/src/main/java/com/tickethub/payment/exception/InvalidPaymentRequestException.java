package com.tickethub.payment.exception;

/** Thrown for requests that are well-formed JSON but not acceptable (e.g. a currency we do not take). Becomes HTTP 400. */
public class InvalidPaymentRequestException extends RuntimeException {
    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
