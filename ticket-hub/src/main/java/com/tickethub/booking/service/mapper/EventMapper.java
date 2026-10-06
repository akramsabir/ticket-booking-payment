package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.request.CreateEventRequest;
import com.tickethub.booking.service.dto.request.EventUpdateRequest;
import com.tickethub.booking.service.dto.response.EventResponse;
import com.tickethub.booking.service.entity.Event;
import com.tickethub.booking.service.enums.EventStatus;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    /**
     * Converts a creation request DTO into a new Event entity.
     */
    public Event toEntity(CreateEventRequest request) {
        if (request == null) {
            return null;
        }

        Event event = new Event();
        event.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
        event.setCategory(request.getCategory());
        event.setDurationMin(request.getDurationMin());
        event.setStatus(EventStatus.DRAFT); // Every new event starts in DRAFT state
        event.setDescription(request.getDescription());
        event.setLanguage(request.getLanguage());
        event.setAgeRating(request.getAgeRating());
        event.setReleaseDate(request.getReleaseDate());
        event.setPosterUrl(request.getPosterUrl());
        return event;
    }

    /**
     * Copies updated values from the request DTO to an existing managed entity.
     */
    public void updateEntity(Event event, EventUpdateRequest request) {
        if (event == null || request == null) {
            return;
        }

        event.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
        event.setCategory(request.getCategory());
        event.setDurationMin(request.getDurationMin());
        event.setDescription(request.getDescription());
        event.setLanguage(request.getLanguage());
        event.setAgeRating(request.getAgeRating());
        event.setReleaseDate(request.getReleaseDate());
        event.setPosterUrl(request.getPosterUrl());
    }

    /**
     * Converts an Event entity to the public-facing response DTO.
     */
    public EventResponse toResponse(Event event) {
        if (event == null) {
            return null;
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .category(event.getCategory())
                .durationMin(event.getDurationMin())
                .status(event.getStatus())
                .description(event.getDescription())
                .language(event.getLanguage())
                .ageRating(event.getAgeRating())
                .releaseDate(event.getReleaseDate())
                .posterUrl(event.getPosterUrl())
                // Type conversion from LocalDateTime (DB/Entity) to Instant (DTO/API)
                .createdAt(event.getCreatedDate() != null ? event.getCreatedDate().toInstant(ZoneOffset.UTC) : null)
                .updatedAt(event.getUpdatedDate() != null ? event.getUpdatedDate().toInstant(ZoneOffset.UTC) : null)
                .build();
    }
}