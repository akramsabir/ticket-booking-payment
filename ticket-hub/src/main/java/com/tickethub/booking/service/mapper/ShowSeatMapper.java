package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.response.ShowSeatResponse;
import com.tickethub.booking.service.entity.Seat;
import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.entity.ShowSeat;
import com.tickethub.booking.service.enums.ShowSeatStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ShowSeatMapper {

    /**
     * Maps Show, Seat, and Price into a new ShowSeat entity
     */
    public ShowSeat toEntity(Show show, Seat seat, BigDecimal price, ShowSeatStatus status) {
        if (show == null || seat == null || price == null) {
            return null;
        }

        return ShowSeat.builder()
                .show(show)
                .seat(seat)
                .price(price)
                .status(status != null ? status : ShowSeatStatus.AVAILABLE)
                .build();
    }

    /**
     * Maps ShowSeat entity to ShowSeatResponse DTO
     */
    public ShowSeatResponse toResponse(ShowSeat showSeat) {
        if (showSeat == null) {
            return null;
        }

        Seat seat = showSeat.getSeat();

        return ShowSeatResponse.builder()
                .id(showSeat.getId())
                .showId(showSeat.getShow() != null ? showSeat.getShow().getId() : null)
                .seatId(seat != null ? seat.getId() : null)
                .rowNumber(seat != null ? seat.getRowLabel() : null)
                .seatNumber(seat != null ? seat.getSeatNumber() : null)
                .seatTier(seat != null ? seat.getTier() : null)
                .price(showSeat.getPrice())
                .status(showSeat.getStatus())
                .build();
    }

    /**
     * Maps a list of ShowSeat entities to a list of ShowSeatResponse DTOs
     */
    public List<ShowSeatResponse> toResponseList(List<ShowSeat> showSeats) {
        if (showSeats == null) {
            return List.of();
        }
        return showSeats.stream()
                .map(this::toResponse)
                .toList();
    }
}