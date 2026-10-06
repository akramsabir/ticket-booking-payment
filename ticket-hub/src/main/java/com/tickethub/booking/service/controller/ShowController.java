package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.request.ShowCreateRequest;
import com.tickethub.booking.service.dto.request.ShowStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.ApiResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.ShowResponse;
import com.tickethub.booking.service.enums.ShowStatus;
import com.tickethub.booking.service.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShowResponse>> createShow(@Valid @RequestBody ShowCreateRequest request) {
        ShowResponse response = showService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Show scheduled successfully", response));
    }

    @GetMapping("/{showId}")
    public ResponseEntity<ApiResponse<ShowResponse>> getShowById(@PathVariable Long showId) {
        ShowResponse response = showService.getShowById(showId);
        return ResponseEntity.ok(ApiResponse.success("Show retrieved successfully", response));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<PageResponse<ShowResponse>>> getShowsByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) ShowStatus status,
            @PageableDefault(sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ShowResponse> response = showService.getShowsByEvent(eventId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Shows retrieved successfully", response));
    }

    @GetMapping("/venues/{venueId}")
    public ResponseEntity<ApiResponse<PageResponse<ShowResponse>>> getShowsByVenueAndDate(
            @PathVariable Long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ShowStatus status,
            @PageableDefault(sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<ShowResponse> response = showService.getShowsByVenueAndDate(venueId, date, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Shows retrieved successfully", response));
    }

    @PatchMapping("/{showId}/status")
    public ResponseEntity<ApiResponse<ShowResponse>> updateShowStatus(
            @PathVariable Long showId,
            @Valid @RequestBody ShowStatusUpdateRequest request
    ) {
        ShowResponse response = showService.updateShowStatus(showId, request);
        return ResponseEntity.ok(ApiResponse.success("Show status updated successfully", response));
    }

    @DeleteMapping("/{showId}")
    public ResponseEntity<ApiResponse<Void>> cancelShow(@PathVariable Long showId) {
        showService.cancelShow(showId);
        return ResponseEntity.ok(ApiResponse.success("Show cancelled successfully", null));
    }
}