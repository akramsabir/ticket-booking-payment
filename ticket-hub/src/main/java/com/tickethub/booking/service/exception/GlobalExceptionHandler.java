package com.tickethub.booking.service.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(
            InvalidStatusTransitionException ex,
            WebRequest request) {
        log.warn("Invalid status transition: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResourceException(
            DuplicateResourceException ex,
            WebRequest request) {
        log.warn("Duplicate resource conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(
            BusinessRuleException ex,
            WebRequest request) {
        log.warn("Business rule violated: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidJsonDataException.class)
    public ResponseEntity<ApiError> handleInvalidJsonData(
            InvalidJsonDataException ex,
            WebRequest request) {
        log.warn("Invalid Json Data: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationErrors(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, null);
    }

    // Fallback catch-all for unexpected server exceptions -> 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Unhandled internal exception occurred", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred", request, null);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            WebRequest request) {
        log.warn("Path not found: {}", ex.getResourcePath());
        return build(HttpStatus.NOT_FOUND, "Endpoint not found: /" + ex.getResourcePath(), request, null);
    }

    // Re-used single handler for ALL invalid enum/type input (Body, Path Variable, Query Param)
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleInvalidInput(Exception ex, WebRequest request) {
        String message = "Invalid input parameter or payload";

        // Case 1: Bad Enum/Value inside URL Path Variable or Query Param
        if (ex instanceof MethodArgumentTypeMismatchException mismatchEx) {
            Class<?> requiredType = mismatchEx.getRequiredType();
            if (requiredType != null && requiredType.isEnum()) {
                message = String.format("Invalid value '%s' for parameter '%s'. Accepted values are: %s",
                        mismatchEx.getValue(), mismatchEx.getName(), Arrays.toString(requiredType.getEnumConstants()));
            } else {
                message = String.format("Parameter '%s' should be of type '%s'",
                        mismatchEx.getName(), requiredType != null ? requiredType.getSimpleName() : "valid type");
            }
        }
        // Case 2: Bad Enum/Value inside JSON Request Body
        else if (ex instanceof HttpMessageNotReadableException readableEx) {
            Throwable rootCause = readableEx.getMostSpecificCause();
            String rootMsg = rootCause.getMessage();
            if (rootMsg != null) {
                int newlineIdx = rootMsg.indexOf('\n');
                String coreText = (newlineIdx != -1) ? rootMsg.substring(0, newlineIdx) : rootMsg;
                coreText = coreText.replaceAll("com\\.[a-zA-Z0-9_.]+\\.", "");
                message = coreText.replace("JSON parse error: ", "").trim();
            }
        }

        log.warn("Input validation mismatch: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    // Helper build method used across all exception handlers
    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            WebRequest request,
            Object details) {
        ApiError error = ApiError.builder()
                .status(status.value())
                .success(false)
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .details(details)
                .build();
        return new ResponseEntity<>(error, status);
    }
}