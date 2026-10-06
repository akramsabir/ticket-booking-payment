package com.tickethub.notification.exception;

/** This notification type is switched off under notification.events.enabled.* */
public class NotificationDisabledException extends RuntimeException {
    public NotificationDisabledException(String message) {
        super(message);
    }
}
