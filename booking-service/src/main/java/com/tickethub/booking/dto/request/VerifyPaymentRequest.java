package com.tickethub.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The three values Razorpay Checkout gives the browser after a successful payment. The browser sends them to
 * POST /bookings/{id}/payment/verify, and Booking forwards them unchanged to payment-service, which does the
 * cryptographic check (Booking never holds Razorpay secrets).
 *
 * @JsonAlias: Razorpay's own snake_case names are accepted too, so the Checkout "handler" response can be posted as-is.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {

    @JsonAlias("razorpay_order_id")
    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @JsonAlias("razorpay_payment_id")
    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @JsonAlias("razorpay_signature")
    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;
}
