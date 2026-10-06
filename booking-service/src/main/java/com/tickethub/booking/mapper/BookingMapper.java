package com.tickethub.booking.mapper;

import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.BookingStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class BookingMapper {
    public Booking toEntity(CreateBookingRequest request) {
        Booking booking = Booking.builder()
                .customerId(request.getCustomerId())
                .showId(request.getShowId())
                .status(BookingStatus.PENDING)
                .build();
        for (String seatId : request.getSeatIds()) {
            booking.addSeat(BookingSeat.builder()
                    .showId(request.getShowId())
                    .seatId(seatId)
                    .build());
        }
        return booking;
    }

    public BookingResponse toResponse(Booking booking) {
        Objects.requireNonNull(booking.getId(), "Booking id must not be null when mapping to response");
        Objects.requireNonNull(booking.getStatus(), "Booking status must not be null when mapping to response");
        List<String> seatIds = booking.getSeats().stream().map(BookingSeat::getSeatId).toList();
        return BookingResponse.builder()
                .id(booking.getId())
                .customerId(booking.getCustomerId())
                .showId(booking.getShowId())
                .status(booking.getStatus())
                .seatIds(seatIds)
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
