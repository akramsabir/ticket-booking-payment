package com.tickethub.booking.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.dto.external.PaymentRequest;
import com.tickethub.booking.dto.external.PaymentResponse;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import com.tickethub.booking.exception.PaymentClientException;
import com.tickethub.booking.exception.PaymentFailedException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The resilient doorway to payment-service. Every call has a rate limiter, a circuit breaker and a retry.
 *
 * Two kinds of failure are told apart on purpose:
 *   - payment-service ANSWERED with 4xx ("invalid signature", "no such order") -> PaymentClientException.
 *     Passed through untouched, never retried, never counted by the circuit breaker.
 *   - payment-service is DOWN / slow / 5xx                                      -> PaymentFailedException
 *     (HTTP 503 for our caller), after retries, and counted by the circuit breaker (blueprint SC-05 / SC-16).
 *
 * Retrying is safe for all of these calls because payment-service is idempotent: same idempotency key, same
 * signature, same cancel or refund repeated - each returns the same result instead of acting twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentClientService {

    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    @RateLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "createPaymentFallback")
    @Retry(name = "paymentService")
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Opening payment order for bookingId={}, amount={}", request.getBookingId(), request.getAmount());
        try {
            return paymentClient.createPayment(request);
        } catch (FeignException.FeignClientException e) {
            throw rejected(e);
        }
    }

    @RateLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "verifyPaymentFallback")
    @Retry(name = "paymentService")
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        log.info("Verifying Razorpay payment for order {}", request.getRazorpayOrderId());
        try {
            return paymentClient.verifyPayment(request);
        } catch (FeignException.FeignClientException e) {
            throw rejected(e);
        }
    }

    @RateLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "listFallback")
    @Retry(name = "paymentService")
    public List<PaymentResponse> getPaymentsForBooking(Long bookingId) {
        try {
            return paymentClient.getPaymentsForBooking(bookingId);
        } catch (FeignException.FeignClientException e) {
            throw rejected(e);
        }
    }

    @RateLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "cancelFallback")
    @Retry(name = "paymentService")
    public List<PaymentResponse> cancelPaymentsForBooking(Long bookingId) {
        log.info("Closing unpaid payments of bookingId={}", bookingId);
        try {
            return paymentClient.cancelPaymentsForBooking(bookingId);
        } catch (FeignException.FeignClientException e) {
            throw rejected(e);
        }
    }

    @RateLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "refundFallback")
    @Retry(name = "paymentService")
    public PaymentResponse refundForBooking(Long bookingId) {
        log.info("Refunding payment of bookingId={}", bookingId);
        try {
            return paymentClient.refundForBooking(bookingId);
        } catch (FeignException.FeignClientException e) {
            throw rejected(e);
        }
    }

    // ------------------------------------------------------------------ fallbacks (one per method; same parameters + Throwable)

    @SuppressWarnings("unused")
    private PaymentResponse createPaymentFallback(PaymentRequest request, Throwable t) {
        throw unavailable("create payment for bookingId=" + request.getBookingId(), t);
    }

    @SuppressWarnings("unused")
    private PaymentResponse verifyPaymentFallback(VerifyPaymentRequest request, Throwable t) {
        throw unavailable("verify payment for order " + request.getRazorpayOrderId(), t);
    }

    @SuppressWarnings("unused")
    private List<PaymentResponse> listFallback(Long bookingId, Throwable t) {
        throw unavailable("list payments for bookingId=" + bookingId, t);
    }

    @SuppressWarnings("unused")
    private List<PaymentResponse> cancelFallback(Long bookingId, Throwable t) {
        throw unavailable("cancel payments for bookingId=" + bookingId, t);
    }

    @SuppressWarnings("unused")
    private PaymentResponse refundFallback(Long bookingId, Throwable t) {
        throw unavailable("refund payment for bookingId=" + bookingId, t);
    }

    /** Business "no" from payment-service passes through; everything else becomes "service unavailable". */
    private RuntimeException unavailable(String what, Throwable t) {
        if (t instanceof PaymentClientException e) {
            return e;
        }
        log.error("payment-service call failed ({}), reason={}: {}", what, t.getClass().getSimpleName(), t.getMessage());
        return new PaymentFailedException("Payment service is currently unavailable. Please try again shortly.");
    }

    /** Turns payment-service's JSON error body ({"message": "..."}) into a PaymentClientException. */
    private PaymentClientException rejected(FeignException e) {
        String message = "Payment service rejected the request";
        try {
            JsonNode body = objectMapper.readTree(e.contentUTF8());
            if (body.hasNonNull("message")) {
                message = body.get("message").asText();
            }
        } catch (Exception ignored) {
            // body was not JSON: keep the generic message
        }
        return new PaymentClientException(e.status(), message);
    }
}
