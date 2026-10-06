package com.tickethub.payment.exception;

/** Thrown when the external payment provider fails us (e.g. refund rejected). Becomes HTTP 502 (Bad Gateway). */
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message) {
        super(message);
    }
}
