package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.request.SetShowPricingRequest;
import com.tickethub.booking.service.dto.request.UpdateTierPriceRequest;
import com.tickethub.booking.service.dto.response.ShowPricingResponse;
import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.service.ShowPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowPricingController {

    private final ShowPricingService showPricingService;

    @PostMapping("/{showId}/pricing")
    public ResponseEntity<List<ShowPricingResponse>> setShowPricing(
            @PathVariable Long showId,
            @Valid @RequestBody SetShowPricingRequest request) {

        return ResponseEntity.ok(
                showPricingService.setShowPricing(showId, request)
        );
    }

    @GetMapping("/{showId}/pricing")
    public ResponseEntity<List<ShowPricingResponse>> getShowPricing(
            @PathVariable Long showId) {

        return ResponseEntity.ok(
                showPricingService.getShowPricing(showId)
        );
    }
    @PatchMapping("/{showId}/pricing/{seatTier}")
    public ResponseEntity<ShowPricingResponse> updateTierPrice(
            @PathVariable Long showId,
            @PathVariable SeatTier seatTier,
            @Valid @RequestBody UpdateTierPriceRequest request) {

        return ResponseEntity.ok(
                showPricingService.updateTierPrice(showId, seatTier, request)
        );
    }
}