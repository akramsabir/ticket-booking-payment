package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.request.CreateVenueRequest;
import com.tickethub.booking.service.dto.request.VenueUpdateRequest;
import com.tickethub.booking.service.dto.response.ApiResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.VenueResponse;
import com.tickethub.booking.service.enums.VenueStatus;
import com.tickethub.booking.service.service.VenueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<ApiResponse<VenueResponse>> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        log.debug("POST /api/venues");
        VenueResponse created = venueService.createVenue(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Venue created successfully", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueResponse> getVenueById(
            @PathVariable("id") @Positive(message = "id must be positive") Long id) {
        log.debug("GET /api/venues/{}", id);
        return ResponseEntity.ok(venueService.getVenueById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<VenueResponse>> getVenues(
            @RequestParam(name = "status", required = false) VenueStatus status,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(name = "size", defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        log.debug("GET /api/venues status={} city={} page={} size={}", status, city, page, size);
        return ResponseEntity.ok(venueService.getVenues(status, city, page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VenueResponse> updateVenue(
            @PathVariable("id") @Positive(message = "id must be positive") Long id,
            @Valid @RequestBody VenueUpdateRequest request) {
        log.debug("PUT /api/venues/{}", id);
        return ResponseEntity.ok(venueService.updateVenue(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateVenue(
            @PathVariable("id") @Positive(message = "id must be positive") Long id) {
        log.debug("DELETE /api/venues/{}", id);
        venueService.deactivateVenue(id);
        return ResponseEntity.ok(ApiResponse.success("Venue deleted successfully"));
    }
}
