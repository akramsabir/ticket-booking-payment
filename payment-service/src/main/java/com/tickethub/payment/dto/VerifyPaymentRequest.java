package com.tickethub.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * What Razorpay Checkout hands back to the browser after a successful payment - the three values the
 * browser must forward to the server so the server can PROVE the payment is genuine.
 *
 * The @JsonAlias lets you paste Razorpay's own snake_case field names straight in:
 *   { "razorpay_order_id": "...", "razorpay_payment_id": "...", "razorpay_signature": "..." }
 */
public record VerifyPaymentRequest(

        @JsonAlias("razorpay_order_id")
        @NotBlank(message = "razorpayOrderId is required")
        String razorpayOrderId,

        @JsonAlias("razorpay_payment_id")
        @NotBlank(message = "razorpayPaymentId is required")
        String razorpayPaymentId,

        @JsonAlias("razorpay_signature")
        @NotBlank(message = "razorpaySignature is required")
        String razorpaySignature
) {
}
