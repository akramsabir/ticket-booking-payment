package com.tickethub.booking.service.dto.request;

import com.tickethub.booking.service.enums.SeatTier;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TierPriceRequest {
    @NotNull(message = "Seat tier is required")
    private SeatTier seatTier;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;
    private String currency;
}
