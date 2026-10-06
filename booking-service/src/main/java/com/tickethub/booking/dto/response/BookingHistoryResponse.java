package com.tickethub.booking.dto.response;

import com.tickethub.booking.enums.BookingStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingHistoryResponse {
    private Long id;
    private Long bookingId;
    private BookingStatus oldStatus;
    private BookingStatus newStatus;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;
}
