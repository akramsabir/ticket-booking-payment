package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.SetShowPricingRequest;
import com.tickethub.booking.service.dto.request.UpdateTierPriceRequest;
import com.tickethub.booking.service.dto.response.ShowPricingResponse;
import com.tickethub.booking.service.enums.SeatTier;
import jakarta.validation.Valid;

import java.util.List;

public interface ShowPricingService {

    List<ShowPricingResponse> setShowPricing(
            Long showId,
            SetShowPricingRequest request
    );

    List<ShowPricingResponse> getShowPricing(Long showId);

    ShowPricingResponse updateTierPrice(Long showId, SeatTier seatTier, @Valid UpdateTierPriceRequest request);
}