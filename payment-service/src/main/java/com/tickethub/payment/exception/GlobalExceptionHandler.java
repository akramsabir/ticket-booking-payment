package com.tickethub.payment.exception;

import com.tickethub.payment.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CENTRAL place that turns Java exceptions into clean JSON errors.
 * Without it, the client would see ugly stack traces. Controllers stay clean: they just throw.
 *
 * @RestControllerAdvice = "watch ALL controllers; when one throws, look for a matching @ExceptionHandler here".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Validation failed (@NotNull, @Email ... on PaymentRequest) -> 400 with a list of which fields are wrong
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", fieldErrors);
    }

    // Broken JSON, or a wrong enum value -> 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body is missing, is not valid JSON, or contains an invalid value", null);
    }

    // /api/v1/payments/abc where a number was expected -> 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' has an invalid value", null);
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidPaymentRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), null);
    }

    // A signature that does not match: never reveal WHY in detail, and log it - repeated hits may be an attack
    @ExceptionHandler(InvalidSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSignature(InvalidSignatureException ex) {
        log.warn("Rejected a request with an invalid signature: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid signature", "The signature could not be verified", null);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), null);
    }

    @ExceptionHandler({DuplicatePaymentException.class, InvalidPaymentStateException.class,
            IdempotencyKeyReuseException.class, PaymentVerificationException.class})
    public ResponseEntity<ErrorResponse> handleBusinessRule(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, "Business rule violation", ex.getMessage(), null);
    }

    // Two requests collided on a unique constraint (e.g. the same webhook delivered twice at the same instant).
    // 409 = "try again": for webhooks Razorpay retries automatically, and the retry is then recognised as a duplicate.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity conflict (likely a concurrent duplicate): {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", "A conflicting update happened at the same time. Please retry.", null);
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ErrorResponse> handleProvider(PaymentProviderException ex) {
        log.error("Payment provider problem: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Payment provider error", ex.getMessage(), null);
    }

    // Safety net for anything unexpected: log details for developers, show a SAFE generic message to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Something went wrong. Please try again later.", null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message,
                                                Map<String, String> fieldErrors) {
        ErrorResponse body = ErrorResponse.of(status.value(), error, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
