package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.SeatTier;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowPricingResponse {
    private Long id;
    private Long showId;
    private SeatTier seatTier;
    private BigDecimal price;
    private String currency;
}
