package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.CreateVenueRequest;
import com.tickethub.booking.service.dto.request.VenueUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.VenueResponse;
import com.tickethub.booking.service.enums.VenueStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public interface VenueService {
    
    VenueResponse createVenue(@Valid CreateVenueRequest request);

    VenueResponse getVenueById(@Positive(message = "id must be positive") Long id);

    PageResponse<VenueResponse> getVenues(VenueStatus status, String city, @Min(value = 0, message = "page must be 0 or more") int page, @Min(value = 1, message = "size must be at least 1") @Max(value = 100, message = "size must be at most 100") int size);

    VenueResponse updateVenue(@Positive(message = "id must be positive") Long id, @Valid VenueUpdateRequest request);

    void deactivateVenue(@Positive(message = "id must be positive") Long id);
}
