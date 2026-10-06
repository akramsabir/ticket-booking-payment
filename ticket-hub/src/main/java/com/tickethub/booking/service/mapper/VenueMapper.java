package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.request.CreateVenueRequest;
import com.tickethub.booking.service.dto.request.VenueUpdateRequest;
import com.tickethub.booking.service.dto.response.VenueResponse;
import com.tickethub.booking.service.entity.Venue;
import com.tickethub.booking.service.enums.VenueStatus;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    public Venue toEntity(CreateVenueRequest request) {
        Venue venue = new Venue();
        venue.setName(request.getName() != null ? request.getName().trim() : null);
        venue.setAddress(request.getAddress());
        venue.setCity(request.getCity() != null ? request.getCity().trim() : null);
        venue.setState(request.getState());
        venue.setCountry(request.getCountry() != null ? request.getCountry().trim() : null);
        venue.setPincode(request.getPincode());
        venue.setLatitude(request.getLatitude());
        venue.setLongitude(request.getLongitude());
        venue.setTimezone(request.getTimezone() != null && !request.getTimezone().isBlank()
                ? request.getTimezone().trim()
                : DEFAULT_TIMEZONE);
        venue.setStatus(VenueStatus.ACTIVE);
        return venue;
    }

    public void updateEntity(Venue venue, VenueUpdateRequest request) {
        venue.setName(request.getName() != null ? request.getName().trim() : null);
        venue.setAddress(request.getAddress());
        venue.setCity(request.getCity() != null ? request.getCity().trim() : null);
        venue.setState(request.getState());
        venue.setCountry(request.getCountry() != null ? request.getCountry().trim() : null);
        venue.setPincode(request.getPincode());
        venue.setLatitude(request.getLatitude());
        venue.setLongitude(request.getLongitude());
        venue.setTimezone(request.getTimezone());
        venue.setStatus(request.getStatus());
    }

    public VenueResponse toResponse(Venue venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .state(venue.getState())
                .country(venue.getCountry())
                .pincode(venue.getPincode())
                .latitude(venue.getLatitude())
                .longitude(venue.getLongitude())
                .timezone(venue.getTimezone())
                .status(venue.getStatus())
                .createdDate(venue.getCreatedDate())
                .updatedDate(venue.getUpdatedDate())
                .build();
    }
}