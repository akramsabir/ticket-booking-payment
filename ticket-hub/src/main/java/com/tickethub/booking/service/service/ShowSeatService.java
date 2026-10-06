package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.response.ShowSeatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShowSeatService {
    List<ShowSeatResponse> generateShowSeats(Long showId);
    List<ShowSeatResponse> getShowSeats(Long showId);
    // Admin/Client retrieval: paginated
    Page<ShowSeatResponse> getShowSeats(Long showId, Pageable pageable);
    // New method: update seats to BOOKED status
    void markSeatsAsBooked(Long showId, List<String> seatIds);
}