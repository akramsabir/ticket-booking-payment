package com.tickethub.payment.dto;

import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The MESSAGE we publish to Kafka (topic "payment-events") after a payment settles.
 * The Notification service reads it and sends the email.
 * THIS IS A CONTRACT with your teammate - tell them before you change any field name.
 * (Unchanged by the Razorpay work: same fields, same event types.)
 *
 * eventType is one of: PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED
 * (PENDING / CANCELLED / INITIATED exist for completeness but are never published.)
 */
public record PaymentEvent(
        String eventType,
        Long paymentId,
        Long bookingId,
        Long userId,
        String customerEmail,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant occurredAt
) {

    public static PaymentEvent from(Payment p) {
        String type = switch (p.getStatus()) {
            case SUCCESS -> "PAYMENT_SUCCESS";
            case FAILED -> "PAYMENT_FAILED";
            case REFUNDED -> "PAYMENT_REFUNDED";
            case PENDING -> "PAYMENT_PENDING";
            case CANCELLED -> "PAYMENT_CANCELLED";
            case INITIATED -> "PAYMENT_INITIATED";
        };
        return new PaymentEvent(
                type,
                p.getId(),
                p.getBookingId(),
                p.getUserId(),
                p.getCustomerEmail(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getFailureReason(),
                Instant.now());
    }
}
