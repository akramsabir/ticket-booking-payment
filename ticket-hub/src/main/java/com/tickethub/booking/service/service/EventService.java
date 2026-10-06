package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.response.EventResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.request.CreateEventRequest;
import com.tickethub.booking.service.dto.request.EventUpdateRequest;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface EventService {
    EventResponse createEvent(@Valid CreateEventRequest request);

    EventResponse getEventById(@Positive(message = "id must be positive") Long id);

    EventResponse updateEvent(@Positive(message = "id must be positive") Long id, EventUpdateRequest request);

    PageResponse<EventResponse> getEvents(EventStatus status, EventCategory category, @Min(value = 0, message = "page must be 0 or more") int page, @Min(value = 1, message = "size must be at least 1") @Max(value = 100, message = "size must be at most 100") int size);

    void archiveEvent(@Positive(message = "id must be positive") Long id);

    EventResponse changeStatus(@Positive(message = "id must be positive") Long id, @NotNull(message = "Status cannot be null") EventStatus status);
}
