package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {

    private Long id;
    private Long hallId;
    private String rowLabel;
    private Integer seatNumber;
    private String seatCode;
    private SeatTier tier;
    private SeatStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}