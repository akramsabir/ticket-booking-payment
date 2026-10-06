package com.tickethub.booking.service.dto.request;

import com.tickethub.booking.service.enums.AgeRating;
import com.tickethub.booking.service.enums.EventCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @NotNull(message = "Category is required")
    private EventCategory category;

    @NotNull(message = "Duration in minutes is required")
    @Positive(message = "Duration must be greater than zero")
    private Integer durationMin;

    private String description;

    @Size(max = 30, message = "Language cannot exceed 30 characters")
    private String language;

    private AgeRating ageRating;

    private LocalDate releaseDate;

    @Size(max = 500, message = "Poster key/URL cannot exceed 500 characters")
    private String posterUrl;
}
