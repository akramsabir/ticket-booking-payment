package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.SeatStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.SeatResponse;
import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatService {

    List<SeatResponse> generateSeatsFromHallLayout(Long hallId);

    PageResponse<SeatResponse> getSeatsByHall(Long hallId, SeatStatus status, SeatTier tier, Pageable pageable);

    SeatResponse getSeatById(Long seatId);

    SeatResponse updateSeatStatus(Long seatId, SeatStatusUpdateRequest request);

    void regenerateSeatsForHall(Long hallId);

    SeatResponse getSeatByHallAndSeatCode(Long hallId, String seatCode);
}