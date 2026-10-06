package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.response.ShowSeatResponse;
import com.tickethub.booking.service.service.ShowSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows/{showId}/seats")
@RequiredArgsConstructor
public class ShowSeatController {

    private final ShowSeatService showSeatService;

    // 1. Admin endpoint: generate inventory for a show
    @PostMapping("/generate")
    public ResponseEntity<List<ShowSeatResponse>> generateShowSeats(@PathVariable Long showId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(showSeatService.generateShowSeats(showId));
    }

    // 2. Blueprint standard: get all seats unpaged (used by UI seat map)
    @GetMapping
    public ResponseEntity<List<ShowSeatResponse>> getShowSeats(@PathVariable Long showId) {
        return ResponseEntity.ok(showSeatService.getShowSeats(showId));
    }

    // 3. Admin / Large layout paginated endpoint
    @GetMapping("/paged")
    public ResponseEntity<Page<ShowSeatResponse>> getShowSeatsPaged(
            @PathVariable Long showId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(showSeatService.getShowSeats(showId, pageable));
    }

    // 4. Update seat statuses to BOOKED when confirmed by booking-service
    @PutMapping("/book")
    public ResponseEntity<Void> markSeatsBooked(
            @PathVariable Long showId,
            @RequestBody List<String> seatIds) {
        showSeatService.markSeatsAsBooked(showId, seatIds);
        return ResponseEntity.ok().build();
    }
}