package com.tickethub.booking.service.mapper;

import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.ShowResponse;
import com.tickethub.booking.service.entity.Show;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShowMapper {

    public ShowResponse toResponse(Show show) {
        if (show == null) {
            return null;
        }

        return ShowResponse.builder()
                .id(show.getId())
                .eventId(show.getEvent() != null ? show.getEvent().getId() : null)
                .eventTitle(show.getEvent() != null ? show.getEvent().getTitle() : null)
                .hallId(show.getHall() != null ? show.getHall().getId() : null)
                .hallName(show.getHall() != null ? show.getHall().getName() : null)
                .venueId(show.getHall() != null && show.getHall().getVenue() != null ? show.getHall().getVenue().getId() : null)
                .venueName(show.getHall() != null && show.getHall().getVenue() != null ? show.getHall().getVenue().getName() : null)
                .startTime(show.getStartTime())
                .endTime(show.getEndTime())
                .status(show.getStatus())
                .createdDate(show.getCreatedDate())
                .updatedDate(show.getUpdatedDate())
                .build();
    }

    public List<ShowResponse> toResponseList(List<Show> shows) {
        if (shows == null || shows.isEmpty()) {
            return Collections.emptyList();
        }
        return shows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PageResponse<ShowResponse> toPageResponse(Page<Show> showPage) {
        return PageResponse.<ShowResponse>builder()
                .content(toResponseList(showPage.getContent()))
                .pageNumber(showPage.getNumber())
                .pageSize(showPage.getSize())
                .totalElements(showPage.getTotalElements())
                .totalPages(showPage.getTotalPages())
                .build();
    }
}