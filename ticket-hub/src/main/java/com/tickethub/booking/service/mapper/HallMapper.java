package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.request.HallCreateRequest;
import com.tickethub.booking.service.dto.request.HallUpdateRequest;
import com.tickethub.booking.service.dto.response.HallResponse;
import com.tickethub.booking.service.entity.Hall;
import com.tickethub.booking.service.entity.Venue;
import com.tickethub.booking.service.exception.InvalidJsonDataException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

@Component
@RequiredArgsConstructor
public class HallMapper {

    private final tools.jackson.databind.ObjectMapper objectMapper;

    public Hall toEntity(HallCreateRequest request, Venue venue) {
        Hall hall = new Hall();
        hall.setVenue(venue);
        hall.setName(request.name().trim());
        hall.setTotalSeats(request.totalSeats());
        hall.setLayoutJson(request.layout() != null ? request.layout().toString() : null);
        return hall;
    }

    public void updateEntity(Hall hall, HallUpdateRequest request) {
        hall.setName(request.name().trim());
        hall.setTotalSeats(request.totalSeats());
        hall.setLayoutJson(request.layout() != null ? request.layout().toString() : null);
        hall.setStatus(request.status());
    }

    public HallResponse toResponse(Hall hall) {
        return new HallResponse(
                hall.getId(),
                hall.getVenue().getId(),
                hall.getVenue().getName(),
                hall.getName(),
                hall.getTotalSeats(),
                parseJson(hall.getLayoutJson()),
                hall.getStatus(),
                hall.getCreatedDate(),
                hall.getUpdatedDate()
        );
    }

    private JsonNode parseJson(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new InvalidJsonDataException("Failed to deserialize hall layout JSON", e);
        }
    }
}