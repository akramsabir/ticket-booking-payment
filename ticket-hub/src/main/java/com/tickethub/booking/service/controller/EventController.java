package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.response.EventResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.request.CreateEventRequest;
import com.tickethub.booking.service.dto.request.EventStatusUpdateRequest;
import com.tickethub.booking.service.dto.request.EventUpdateRequest;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import com.tickethub.booking.service.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        log.debug("POST /api/events: {}", request);
        EventResponse created = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable("id") @Positive(message = "id must be positive") Long id) {
        log.debug("GET /api/events/{}", id);
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<EventResponse>> getEvents(
            @RequestParam(name = "status", required = false) EventStatus status,
            @RequestParam(name = "category", required = false) EventCategory category,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(name = "size", defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        log.debug("GET /api/events status={} category={} page={} size={}", status, category, page, size);
        return ResponseEntity.ok(eventService.getEvents(status, category, page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable("id") @Positive(message = "id must be positive") Long id,
            @Valid @RequestBody EventUpdateRequest request) {
        log.debug("PUT /api/events/{}", id);
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EventResponse> changeStatus(
            @PathVariable("id") @Positive(message = "id must be positive") Long id,
            @Valid @RequestBody EventStatusUpdateRequest request) {
        log.debug("PATCH /api/events/{}/status -> {}", id, request.getStatus());
        return ResponseEntity.ok(eventService.changeStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveEvent(
            @PathVariable("id") @Positive(message = "id must be positive") Long id) {
        log.debug("DELETE /api/events/{}", id);
        eventService.archiveEvent(id);
        return ResponseEntity.noContent().build();   // 204
    }
}