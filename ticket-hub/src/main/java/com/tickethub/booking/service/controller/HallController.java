package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.request.HallCreateRequest;
import com.tickethub.booking.service.dto.request.HallUpdateRequest;
import com.tickethub.booking.service.dto.response.ApiResponse;
import com.tickethub.booking.service.dto.response.HallResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.enums.HallStatus;
import com.tickethub.booking.service.service.HallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    @PostMapping("/api/v1/venues/{venueId}/halls")
    public ResponseEntity<ApiResponse<HallResponse>> createHall(
            @PathVariable Long venueId,
            @Valid @RequestBody HallCreateRequest request
    ) {
        HallResponse response = hallService.createHall(venueId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Hall created successfully", response));
    }

    @GetMapping("/api/v1/venues/{venueId}/halls")
    public ResponseEntity<ApiResponse<PageResponse<HallResponse>>> getHallsByVenue(
            @PathVariable Long venueId,
            @RequestParam(required = false) HallStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<HallResponse> response = hallService.getHallsByVenue(venueId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Halls retrieved successfully", response));
    }

    @GetMapping("/api/v1/halls/{hallId}")
    public ResponseEntity<ApiResponse<HallResponse>> getHallById(@PathVariable Long hallId) {
        HallResponse response = hallService.getHallById(hallId);
        return ResponseEntity.ok(ApiResponse.success("Hall retrieved successfully", response));
    }

    @PutMapping("/api/v1/halls/{hallId}")
    public ResponseEntity<ApiResponse<HallResponse>> updateHall(
            @PathVariable Long hallId,
            @Valid @RequestBody HallUpdateRequest request
    ) {
        HallResponse response = hallService.updateHall(hallId, request);
        return ResponseEntity.ok(ApiResponse.success("Hall updated successfully", response));
    }

    @DeleteMapping("/api/v1/halls/{hallId}")
    public ResponseEntity<ApiResponse<Void>> deleteHall(@PathVariable Long hallId) {
        hallService.deleteHall(hallId);
        return ResponseEntity.ok(ApiResponse.success("Hall deactivated successfully", null));
    }
}
