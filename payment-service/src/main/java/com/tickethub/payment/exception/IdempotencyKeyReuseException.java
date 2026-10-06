package com.tickethub.payment.exception;

/**
 * Thrown when an idempotency key that was already used for one payment is sent again with DIFFERENT data
 * (another booking or another amount). Replaying the same request is fine; reusing the key for something else is a bug
 * on the caller's side, so we refuse instead of silently returning an unrelated payment. Becomes HTTP 409.
 */
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String key) {
        super("Idempotency key '" + key + "' was already used for a different payment request");
    }
}
