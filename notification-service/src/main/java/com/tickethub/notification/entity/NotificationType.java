package com.tickethub.notification.entity;

/** What the notification is about. */
public enum NotificationType {
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_EXPIRED,
    CUSTOM;

    /** "BOOKING_CONFIRMED" -> "booking-confirmed" (the key used under notification.events.enabled). */
    public String configKey() {
        return name().toLowerCase().replace('_', '-');
    }
}
