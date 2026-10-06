package com.tickethub.payment.controller;

import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.dto.VerifyPaymentRequest;
import com.tickethub.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CONTROLLER = the FRONT DESK. It only deals with HTTP: receive a request, hand it to the
 * service, return a response with the right status code. NO business logic here.
 *
 * These endpoints are for OTHER SERVICES (Booking). Do not expose them to the internet through your gateway -
 * the only endpoint Razorpay must reach is the webhook (see PaymentWebhookController).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/v1/payments -> create a Razorpay ORDER for a booking.
     * The response carries what Razorpay Checkout needs (razorpayKeyId, providerOrderId, amountInMinorUnits).
     * The same idempotencyKey (or an already-open order for the booking) returns the SAME order.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    /**
     * POST /api/v1/payments/verify -> prove a checkout result is genuine.
     * Body: the three values Razorpay Checkout gave the browser (razorpay_order_id, razorpay_payment_id, razorpay_signature).
     * 200 + status SUCCESS = paid. 400 = signature did not match.
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    /** GET /api/v1/payments/5 -> fetch one payment. */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /** GET /api/v1/payments/booking/12 -> all payment attempts for booking 12 (newest first). Booking service uses this. */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsForBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentsForBooking(bookingId));
    }

    /** POST /api/v1/payments/5/sync -> ask Razorpay what really happened and update our record (also handy on localhost without webhooks). */
    @PostMapping("/{id}/sync")
    public ResponseEntity<PaymentResponse> sync(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.syncPayment(id));
    }

    /** POST /api/v1/payments/5/refund -> give the money back. Repeating it is harmless. */
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }

    /** POST /api/v1/payments/booking/12/refund -> refund the successful payment of booking 12 (booking cancelled after confirmation). */
    @PostMapping("/booking/{bookingId}/refund")
    public ResponseEntity<PaymentResponse> refundForBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.refundForBooking(bookingId));
    }

    /**
     * POST /api/v1/payments/booking/12/cancel -> the booking was cancelled/expired: close its unpaid payments.
     * If the customer actually paid just now, that payment is returned as SUCCESS (not cancelled) so Booking can react.
     */
    @PostMapping("/booking/{bookingId}/cancel")
    public ResponseEntity<List<PaymentResponse>> cancelForBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(paymentService.cancelPaymentsForBooking(bookingId));
    }
}
