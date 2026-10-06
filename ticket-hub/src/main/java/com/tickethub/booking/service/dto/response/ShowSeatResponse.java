package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.enums.ShowSeatStatus;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeatResponse {
    private Long id;
    private Long showId;
    private Long seatId;
    private String rowNumber;
    private Integer seatNumber;
    private SeatTier seatTier;
    private BigDecimal price;
    private ShowSeatStatus status;
}