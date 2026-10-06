package com.tickethub.booking.service.dto.response;


import com.tickethub.booking.service.enums.HallStatus;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record HallResponse(
        Long id,
        Long venueId,
        String venueName,
        String name,
        Integer totalSeats,
        JsonNode layout,
        HallStatus status,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {}