package com.tickethub.booking.service.enums;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    /**
     * Enforces the state machine rules for event lifecycle transitions.
     */
    public boolean canTransitionTo(EventStatus target) {
        if (target == null) {
            return false;
        }

        return switch (this) {
            case DRAFT     -> target == PUBLISHED || target == ARCHIVED;
            case PUBLISHED -> target == ARCHIVED;
            case ARCHIVED  -> false; // Final terminal state
        };
    }
}