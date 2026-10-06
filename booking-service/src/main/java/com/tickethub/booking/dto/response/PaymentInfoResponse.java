package com.tickethub.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

/**
 * Everything the browser needs to open Razorpay Checkout for a booking, plus the payment's current status.
 *
 *   new Razorpay({ key: razorpayKeyId, order_id: razorpayOrderId, amount: amountInMinorUnits, currency: currency, ... })
 *
 * razorpayKeyId is Razorpay's PUBLIC key id (safe to expose). It is only present while the payment can still be paid.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentInfoResponse {
    private Long paymentId;
    /** PENDING (waiting for the customer), FAILED (last attempt failed - may retry), SUCCESS, CANCELLED, REFUNDED */
    private String status;
    private String razorpayKeyId;
    private String razorpayOrderId;
    private BigDecimal amount;
    private long amountInMinorUnits;
    private String currency;
    private String failureReason;
}
