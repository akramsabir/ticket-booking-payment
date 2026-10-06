package com.tickethub.booking.service.service;


import com.tickethub.booking.service.dto.request.HallCreateRequest;
import com.tickethub.booking.service.dto.request.HallUpdateRequest;
import com.tickethub.booking.service.dto.response.HallResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.enums.HallStatus;
import org.springframework.data.domain.Pageable;

public interface HallService {

    HallResponse createHall(Long venueId, HallCreateRequest request);

    HallResponse updateHall(Long hallId, HallUpdateRequest request);

    HallResponse getHallById(Long hallId);

    PageResponse<HallResponse> getHallsByVenue(Long venueId, HallStatus status, Pageable pageable);

    void deleteHall(Long hallId);
}
