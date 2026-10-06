package com.tickethub.booking.service.exception;

import com.tickethub.booking.service.enums.EventStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(EventStatus from, EventStatus to) {
        super("Cannot change event status from " + from + " to " + to);
    }
    // Add this constructor for custom messages
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
