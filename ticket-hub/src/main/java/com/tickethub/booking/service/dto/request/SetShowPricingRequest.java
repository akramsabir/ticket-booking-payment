package com.tickethub.booking.service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetShowPricingRequest {
    @NotEmpty(message = "At least one tier pricing configuration must be provided")
    @Valid
    private List<TierPriceRequest> pricingTiers;
}
