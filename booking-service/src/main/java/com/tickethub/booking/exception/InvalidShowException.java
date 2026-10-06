package com.tickethub.booking.exception;

/**
 * Thrown when event-service reports the show doesn't exist, isn't
 * bookable (CANCELLED/COMPLETED), or a requested seat isn't valid -
 * or when event-service can't be reached at all.
 */
public class InvalidShowException extends RuntimeException {
    public InvalidShowException(String message) {
        super(message);
    }
}
