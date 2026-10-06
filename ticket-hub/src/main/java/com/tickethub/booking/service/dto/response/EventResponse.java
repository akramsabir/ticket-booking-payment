package com.tickethub.booking.service.dto.response;

import com.tickethub.booking.service.enums.AgeRating;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {

    private Long id;
    private String title;
    private EventCategory category;
    private Integer durationMin;
    private EventStatus status;
    private String description;
    private String language;
    private AgeRating ageRating;
    private LocalDate releaseDate;
    private String posterUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
