package com.tickethub.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowMetadataDto {
    private Long id;
    private Long eventId;
    private Long hallId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // SCHEDULED, BOOKING_OPEN, BOOKING_CLOSED, CANCELLED, COMPLETED
}