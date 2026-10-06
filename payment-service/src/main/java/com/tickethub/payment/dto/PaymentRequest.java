package com.tickethub.payment.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO = the shape of the JSON that comes IN to our API. The Booking service sends this to create a payment ORDER.
 *
 * What is gone compared to the mock version: "paymentMethod" and "paymentToken". With Razorpay the customer
 * chooses card/UPI/netbanking INSIDE Razorpay Checkout (in the browser) and we never see card data or tokens.
 *
 * idempotencyKey: send the same key again and you get the SAME payment back instead of a second order.
 * (Booking uses its own booking key, so a retry after a timeout can never create two orders.)
 */
public record PaymentRequest(

        @NotNull(message = "bookingId is required")
        Long bookingId,

        @NotNull(message = "userId is required")
        Long userId,

        @NotBlank(message = "customerEmail is required")
        @Email(message = "customerEmail must be a valid email")
        String customerEmail,

        // Razorpay's minimum order is 1.00 in the main currency (100 paise)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "1.00", message = "amount must be at least 1.00")
        @Digits(integer = 10, fraction = 2, message = "amount can have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter uppercase code like INR")
        String currency,

        @Size(max = 100, message = "idempotencyKey can be at most 100 characters")
        String idempotencyKey
) {
}
