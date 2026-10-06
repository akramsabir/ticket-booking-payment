package com.tickethub.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tickethub.booking.enums.BookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long customerId;
    private Long showId;
    private BookingStatus status;
    private List<String> seatIds;
    private BigDecimal totalAmount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Present while the booking waits for payment: what the browser needs to open Razorpay Checkout. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PaymentInfoResponse payment;
}
