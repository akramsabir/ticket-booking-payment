package com.tickethub.booking.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What payment-service answers. Field names match payment-service's PaymentResponse exactly.
 * status: PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED (INITIATED is internal to payment-service).
 *
 * Only SUCCESS means "the money is captured". A booking is confirmed on SUCCESS and on nothing else.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // payment-service may add fields later without breaking us
public class PaymentResponse {
    private Long paymentId;
    private Long bookingId;
    private BigDecimal amount;
    private long amountInMinorUnits;
    private String currency;
    private String paymentMethod;
    private String status;
    private String providerOrderId;
    private String providerPaymentId;
    private String razorpayKeyId;
    private String failureReason;
    private Instant createdAt;

    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    /** The customer can still pay on this order (nothing captured, order not closed). */
    public boolean isPayable() {
        return "PENDING".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }
}
