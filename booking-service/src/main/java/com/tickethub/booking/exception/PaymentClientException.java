package com.tickethub.booking.exception;

/**
 * payment-service ANSWERED, and the answer was "no" (HTTP 4xx): invalid signature, unknown order, business-rule conflict...
 * That is different from payment-service being DOWN (PaymentFailedException).
 *
 * The difference matters for resilience: retrying a "no" is pointless and it must not trip the circuit breaker,
 * so this exception is listed under ignore-exceptions in application.yml.
 */
public class PaymentClientException extends RuntimeException {

    private final int status;

    public PaymentClientException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status payment-service answered with. */
    public int getStatus() {
        return status;
    }
}
