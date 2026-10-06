package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.ShowCreateRequest;
import com.tickethub.booking.service.dto.request.ShowStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.ShowResponse;
import com.tickethub.booking.service.enums.ShowStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ShowService {

    ShowResponse createShow(ShowCreateRequest request);

    ShowResponse getShowById(Long showId);

    PageResponse<ShowResponse> getShowsByEvent(Long eventId, ShowStatus status, Pageable pageable);

    PageResponse<ShowResponse> getShowsByVenueAndDate(Long venueId, LocalDate date, ShowStatus status, Pageable pageable);

    ShowResponse updateShowStatus(Long showId, ShowStatusUpdateRequest request);

    void cancelShow(Long showId);
}