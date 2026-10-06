package com.tickethub.payment.dto;

import java.time.Instant;
import java.util.Map;

/**
 * ONE consistent shape for every error our API returns (a TicketHub standard).
 * fieldErrors is only filled for validation problems, e.g. {"amount": "amount is required"}.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, fieldErrors, Instant.now());
    }
}
