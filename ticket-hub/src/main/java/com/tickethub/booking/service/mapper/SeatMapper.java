package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.SeatResponse;
import com.tickethub.booking.service.entity.Seat;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SeatMapper {

    public SeatResponse toResponse(Seat seat) {
        if (seat == null) {
            return null;
        }

        return SeatResponse.builder()
                .id(seat.getId())
                .hallId(seat.getHall() != null ? seat.getHall().getId() : null)
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .seatCode(seat.getSeatCode())
                .tier(seat.getTier())
                .status(seat.getStatus())
                .createdDate(seat.getCreatedDate())
                .updatedDate(seat.getUpdatedDate())
                .build();
    }

    public List<SeatResponse> toResponseList(List<Seat> seats) {
        if (seats == null || seats.isEmpty()) {
            return Collections.emptyList();
        }

        return seats.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PageResponse<SeatResponse> toPageResponse(Page<Seat> seatPage) {
        List<SeatResponse> content = toResponseList(seatPage.getContent());

        return PageResponse.<SeatResponse>builder()
                .content(content)
                .pageNumber(seatPage.getNumber())
                .pageSize(seatPage.getSize())
                .totalElements(seatPage.getTotalElements())
                .totalPages(seatPage.getTotalPages())
                .isLast(seatPage.isLast())
                .build();
    }
}