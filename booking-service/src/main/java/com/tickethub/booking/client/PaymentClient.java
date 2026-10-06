package com.tickethub.booking.client;

import com.tickethub.booking.dto.external.PaymentRequest;
import com.tickethub.booking.dto.external.PaymentResponse;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** Raw HTTP contract with payment-service. Use PaymentClientService (it adds timeouts/retry/circuit breaker), not this directly. */
@FeignClient(
        name = "payment-service",
        url = "${services.payment-service.url:http://localhost:8081}"
)
public interface PaymentClient {

    /** Opens a Razorpay order for the booking. */
    @PostMapping("/api/v1/payments")
    PaymentResponse createPayment(@RequestBody PaymentRequest request);

    /** Proves the Razorpay Checkout result is genuine; returns the payment (SUCCESS when captured). */
    @PostMapping("/api/v1/payments/verify")
    PaymentResponse verifyPayment(@RequestBody VerifyPaymentRequest request);

    @GetMapping("/api/v1/payments/{id}")
    PaymentResponse getPaymentById(@PathVariable("id") Long id);

    @GetMapping("/api/v1/payments/booking/{bookingId}")
    List<PaymentResponse> getPaymentsForBooking(@PathVariable("bookingId") Long bookingId);

    /** Booking cancelled/expired before payment: close the unpaid payments (returns SUCCESS if the customer paid just now). */
    @PostMapping("/api/v1/payments/booking/{bookingId}/cancel")
    List<PaymentResponse> cancelPaymentsForBooking(@PathVariable("bookingId") Long bookingId);

    /** Confirmed booking cancelled: give the money back. Repeating is harmless. */
    @PostMapping("/api/v1/payments/booking/{bookingId}/refund")
    PaymentResponse refundForBooking(@PathVariable("bookingId") Long bookingId);
}
