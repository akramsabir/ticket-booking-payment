package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.SetShowPricingRequest;
import com.tickethub.booking.service.dto.request.TierPriceRequest;
import com.tickethub.booking.service.dto.request.UpdateTierPriceRequest;
import com.tickethub.booking.service.dto.response.ShowPricingResponse;
import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.entity.ShowPricing;
import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.exception.BusinessRuleException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.ShowPricingMapper;
import com.tickethub.booking.service.repository.ShowPricingRepository;
import com.tickethub.booking.service.repository.ShowRepository;
import com.tickethub.booking.service.service.ShowPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowPricingServiceImpl implements ShowPricingService {

    private final ShowPricingRepository showPricingRepository;
    private final ShowRepository showRepository;
    private final ShowPricingMapper showPricingMapper;

    @Override
    @Transactional
    public List<ShowPricingResponse> setShowPricing(Long showId, SetShowPricingRequest request) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        // Prevent duplicate tiers in request payload
        long distinctTierCount = request.getPricingTiers().stream()
                .map(TierPriceRequest::getSeatTier)
                .distinct()
                .count();

        if (distinctTierCount != request.getPricingTiers().size()) {
            throw new BusinessRuleException("Duplicate seat tiers provided in pricing configuration");
        }

        // Wipe previous pricing configuration to allow clean updates
        showPricingRepository.deleteByShowId(showId);
        showPricingRepository.flush();

        // Map requests to entities via ShowPricingMapper
        List<ShowPricing> pricingList = request.getPricingTiers().stream()
                .map(tierRequest -> showPricingMapper.toEntity(tierRequest, show))
                .toList();

        List<ShowPricing> savedPricing = showPricingRepository.saveAll(pricingList);

        // Convert to response DTO list via ShowPricingMapper
        return showPricingMapper.toResponseList(savedPricing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowPricingResponse> getShowPricing(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show", "id", showId);
        }

        List<ShowPricing> pricings = showPricingRepository.findByShowId(showId);
        return showPricingMapper.toResponseList(pricings);
    }

    @Override
    @Transactional
    public ShowPricingResponse updateTierPrice(Long showId, SeatTier seatTier, UpdateTierPriceRequest request) {
        ShowPricing pricing = showPricingRepository.findByShowIdAndSeatTier(showId, seatTier)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ShowPricing", "showId and seatTier", showId + " / " + seatTier));

        pricing.setPrice(request.getPrice());
        ShowPricing updated = showPricingRepository.save(pricing);

        return showPricingMapper.toResponse(updated);
    }
}