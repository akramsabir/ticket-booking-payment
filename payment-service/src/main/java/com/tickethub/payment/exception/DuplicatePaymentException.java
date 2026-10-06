package com.tickethub.payment.exception;

/** Thrown when a booking has already been paid successfully. Stops double charging. Becomes HTTP 409. */
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(Long bookingId) {
        super("Booking " + bookingId + " has already been paid successfully");
    }
}
