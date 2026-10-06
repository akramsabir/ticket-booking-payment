package com.tickethub.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * What Booking sends to payment-service to open a Razorpay ORDER for a booking.
 * (Contract owned by payment-service: POST /api/v1/payments.)
 *
 * Gone compared to the mock version: paymentMethod and paymentToken. The customer picks the method inside
 * Razorpay Checkout, so Booking never sees or invents a token any more.
 *
 * The AMOUNT is the one Booking calculated from event-service seat prices - the browser can never influence it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private Long bookingId;
    private Long userId;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    /** Same key => same payment. Makes retries (our own Resilience4j retry, a timeout...) safe. */
    private String idempotencyKey;
}
