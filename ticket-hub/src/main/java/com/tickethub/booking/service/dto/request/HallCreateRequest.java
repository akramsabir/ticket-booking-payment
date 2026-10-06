package com.tickethub.booking.service.dto.request;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record HallCreateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "totalSeats is required")
        @Min(value = 1, message = "totalSeats must be at least 1")
        @Max(value = 5000, message = "totalSeats must be at most 5000")
        Integer totalSeats,

        JsonNode layout
) {
    @JsonIgnore
    @AssertTrue(message = "layout must be at most 100000 characters")
    public boolean isLayoutSizeValid() {
        return layout == null || layout.toString().length() <= 100_000;
    }
}