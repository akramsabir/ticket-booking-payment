package com.tickethub.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tickethub.payment.entity.Payment;
import com.tickethub.payment.entity.PaymentMethod;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.gateway.AmountUtil;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for the JSON that goes OUT of our API. We deliberately do NOT include internal things
 * like customerEmail or userId - the client only gets what it needs.
 *
 * To open Razorpay Checkout the browser needs exactly three things from here:
 *   razorpayKeyId  (public key),  providerOrderId (the order),  amountInMinorUnits (paise).
 * razorpayKeyId is only filled while the payment can still be paid (PENDING / FAILED); it is a public value,
 * not a secret, but there is no reason to hand it out for finished payments.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        long amountInMinorUnits,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String providerOrderId,
        String providerPaymentId,
        String razorpayKeyId,
        String failureReason,
        Instant createdAt
) {

    /** Converts a database Payment (entity) into a PaymentResponse (DTO). checkoutKey = the public Razorpay key id. */
    public static PaymentResponse from(Payment p, String checkoutKey) {
        boolean payable = p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.FAILED;
        return new PaymentResponse(
                p.getId(),
                p.getBookingId(),
                p.getAmount(),
                AmountUtil.toMinorUnits(p.getAmount()),
                p.getCurrency(),
                p.getPaymentMethod(),
                p.getStatus(),
                p.getProviderOrderId(),
                p.getProviderPaymentId(),
                payable ? checkoutKey : null,
                p.getFailureReason(),
                p.getCreatedAt());
    }
}
