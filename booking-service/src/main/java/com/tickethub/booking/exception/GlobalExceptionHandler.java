package com.tickethub.booking.exception;

import com.tickethub.booking.constant.BookingConstants;
import com.tickethub.booking.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Central place where every exception is translated into the same
 * ApiErrorResponse shape, so API consumers never see a raw stack trace.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.warn("Validation failed on {} {} - {}", request.getMethod(), request.getRequestURI(), details);

        return build(HttpStatus.BAD_REQUEST, BookingConstants.VALIDATION_FAILED, request, details);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                             HttpServletRequest request) {
        log.warn("Resource not found on {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<ApiErrorResponse> handleSeatConflict(SeatAlreadyBookedException ex,
                                                                 HttpServletRequest request) {
        log.warn("Seat conflict on {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                  HttpServletRequest request) {
        // Safety net: catches the unique-constraint violation directly if it
        // ever bypasses the explicit pre-check in the service layer
        // (e.g. under a race between two concurrent requests).
        log.warn("Data integrity violation on {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "The requested seat(s) could not be booked - already taken", request, null);
    }

    @ExceptionHandler(InvalidShowException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidShow(InvalidShowException ex,
                                                                 HttpServletRequest request) {
        log.warn("Invalid show/seat reference on {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException ex,
                                                                   HttpServletRequest request) {
        log.warn("Invalid request on {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }


    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentFailed(PaymentFailedException ex, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null);
    }

    // payment-service answered "no" (invalid signature, unknown order ...): pass its verdict on with the matching status
    @ExceptionHandler(PaymentClientException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentRejected(PaymentClientException ex, HttpServletRequest request) {
        log.warn("payment-service rejected {} {} - {} {}", request.getMethod(), request.getRequestURI(), ex.getStatus(), ex.getMessage());
        HttpStatus status = switch (ex.getStatus()) {
            case 400, 422 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return build(status, ex.getMessage(), request, null);
    }

    // Two requests changed the same booking at the same instant (e.g. browser verify + reconcile job): ask the client to retry
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex,
                                                                     HttpServletRequest request) {
        log.warn("Concurrent update on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "The booking was updated at the same time. Please retry.", request, null);
    }

    // Thrown by @PreAuthorize and by our own ownership checks. WITHOUT this handler the catch-all below would turn
    // a "forbidden" into a 500 error, because controller exceptions reach this class before Spring Security sees them.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request, null);
    }

    // The same Idempotency-Key was used by a different customer
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, BookingConstants.INTERNAL_ERROR, request, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message,
                                                     HttpServletRequest request, List<String> details) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
