package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.request.TierPriceRequest;
import com.tickethub.booking.service.dto.response.ShowPricingResponse;
import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.entity.ShowPricing;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShowPricingMapper {

    /**
     * Maps incoming TierPriceRequest and parent Show into a ShowPricing entity
     */
    public ShowPricing toEntity(TierPriceRequest request, Show show) {
        if (request == null || show == null) {
            return null;
        }

        ShowPricing pricing = new ShowPricing();
        pricing.setShow(show);
        pricing.setSeatTier(request.getSeatTier());
        pricing.setPrice(request.getPrice());

        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            pricing.setCurrency(request.getCurrency());
        } else {
            pricing.setCurrency("INR");
        }

        return pricing;
    }

    /**
     * Maps ShowPricing entity to ShowPricingResponse DTO
     */
    public ShowPricingResponse toResponse(ShowPricing pricing) {
        if (pricing == null) {
            return null;
        }

        return ShowPricingResponse.builder()
                .id(pricing.getId())
                .showId(pricing.getShow() != null ? pricing.getShow().getId() : null)
                .seatTier(pricing.getSeatTier())
                .price(pricing.getPrice())
                .currency(pricing.getCurrency())
                .build();
    }

    /**
     * Maps list of ShowPricing entities to list of ShowPricingResponse DTOs
     */
    public List<ShowPricingResponse> toResponseList(List<ShowPricing> pricingList) {
        if (pricingList == null) {
            return List.of();
        }

        return pricingList.stream()
                .map(this::toResponse)
                .toList();
    }
}