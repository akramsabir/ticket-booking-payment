package com.tickethub.booking.constant;

/**
 * Centralized constants for the booking-service.
 * Avoids magic strings scattered across the codebase.
 */
public final class BookingConstants {

    private BookingConstants() {
        // utility class - prevent instantiation
    }

    // ---- Error messages ----
    public static final String BOOKING_NOT_FOUND = "Booking not found for id: %s";
    public static final String SEAT_ALREADY_BOOKED = "Seat %s for show %s is already booked";
    public static final String INVALID_SHOW = "Show id must be provided and valid";
    public static final String VALIDATION_FAILED = "Request validation failed";
    public static final String INTERNAL_ERROR = "An unexpected error occurred. Please try again later.";

    // ---- Audit ----
    public static final String SYSTEM_USER = "system";

    // ---- Misc ----
    public static final String DEFAULT_SORT_FIELD = "createdAt";
}
