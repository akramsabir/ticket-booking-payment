package com.tickethub.booking.service.entity;

import com.tickethub.booking.service.enums.AgeRating;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "events")
public class Event extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventCategory category;          // MOVIE, CONCERT, ...

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    // Secondary / UI attributes (nullable for now)
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 30)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", length = 10)
    private AgeRating ageRating;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;
}
