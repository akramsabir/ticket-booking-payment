package com.tickethub.booking.service.controller;

import com.tickethub.booking.service.dto.request.SeatStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.ApiResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.SeatResponse;
import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/api/v1/halls/{hallId}/seats/generate")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> generateSeats(@PathVariable Long hallId) {
        List<SeatResponse> response = seatService.generateSeatsFromHallLayout(hallId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seats generated successfully", response));
    }

    @GetMapping("/api/v1/halls/{hallId}/seats")
    public ResponseEntity<ApiResponse<PageResponse<SeatResponse>>> getSeatsByHall(
            @PathVariable Long hallId,
            @RequestParam(required = false) SeatStatus status,
            @RequestParam(required = false) SeatTier tier,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "rowLabel", direction = Sort.Direction.ASC),
                    @SortDefault(sort = "seatNumber", direction = Sort.Direction.ASC)
            }) Pageable pageable
    ) {
        PageResponse<SeatResponse> response = seatService.getSeatsByHall(hallId, status, tier, pageable);
        return ResponseEntity.ok(ApiResponse.success("Seats retrieved successfully", response));
    }

    @GetMapping("/api/v1/seats/{seatId}")
    public ResponseEntity<ApiResponse<SeatResponse>> getSeatById(@PathVariable Long seatId) {
        SeatResponse response = seatService.getSeatById(seatId);
        return ResponseEntity.ok(ApiResponse.success("Seat retrieved successfully", response));
    }

    @PatchMapping("/api/v1/seats/{seatId}/status")
    public ResponseEntity<ApiResponse<SeatResponse>> updateSeatStatus(
            @PathVariable Long seatId,
            @Valid @RequestBody SeatStatusUpdateRequest request
    ) {
        SeatResponse response = seatService.updateSeatStatus(seatId, request);
        return ResponseEntity.ok(ApiResponse.success("Seat status updated successfully", response));
    }

    @PostMapping("/api/v1/halls/{hallId}/seats/regenerate")
    public ResponseEntity<ApiResponse<Void>> regenerateSeats(@PathVariable Long hallId) {
        seatService.regenerateSeatsForHall(hallId);
        return ResponseEntity.ok(ApiResponse.success("Seats regenerated successfully", null));
    }

    @GetMapping("/api/v1/halls/{hallId}/seats/code/{seatCode}")
    public ResponseEntity<ApiResponse<SeatResponse>> getSeatByCode(
            @PathVariable Long hallId,
            @PathVariable String seatCode
    ) {
        SeatResponse response = seatService.getSeatByHallAndSeatCode(hallId, seatCode);
        return ResponseEntity.ok(ApiResponse.success("Seat retrieved successfully", response));
    }
}