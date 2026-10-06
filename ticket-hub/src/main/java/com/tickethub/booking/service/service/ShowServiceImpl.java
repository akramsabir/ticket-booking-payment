package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.request.ShowCreateRequest;
import com.tickethub.booking.service.dto.request.ShowStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.ShowResponse;
import com.tickethub.booking.service.entity.Event;
import com.tickethub.booking.service.entity.Hall;
import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.enums.ShowStatus;
import com.tickethub.booking.service.exception.BusinessRuleException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.ShowMapper;
import com.tickethub.booking.service.repository.EventRepository;
import com.tickethub.booking.service.repository.HallRepository;
import com.tickethub.booking.service.repository.ShowRepository;
import com.tickethub.booking.service.service.ShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowServiceImpl implements ShowService {

    private static final int CLEANING_BUFFER_MINUTES = 20;

    private final ShowRepository showRepository;
    private final EventRepository eventRepository;
    private final HallRepository hallRepository;
    private final ShowMapper showMapper;

    @Override
    @Transactional
    public ShowResponse createShow(ShowCreateRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", request.getEventId()));

        Hall hall = hallRepository.findById(request.getHallId())
                .orElseThrow(() -> new ResourceNotFoundException("Hall", "id", request.getHallId()));

        // Calculate endTime if not provided (duration + cleaning buffer)
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();
        if (endTime == null) {
            int duration = (event.getDurationMin() != null && event.getDurationMin() > 0)
                    ? event.getDurationMin()
                    : 120;
            endTime = startTime.plusMinutes(duration + CLEANING_BUFFER_MINUTES);
        }

        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("Show end time must be after start time");
        }

        // Validate hall schedule collision
        boolean hasConflict = showRepository.hasOverlappingShow(hall.getId(), startTime, endTime, null);
        if (hasConflict) {
            throw new BusinessRuleException(
                    String.format("Hall '%s' already has another show scheduled between %s and %s",
                            hall.getName(), startTime, endTime)
            );
        }

        Show show = Show.builder()
                .event(event)
                .hall(hall)
                .startTime(startTime)
                .endTime(endTime)
                .status(ShowStatus.SCHEDULED)
                .build();

        Show savedShow = showRepository.save(show);
        log.info("Successfully scheduled Show ID: {} in Hall: {} for Event: {}",
                savedShow.getId(), hall.getId(), event.getId());

        return showMapper.toResponse(savedShow);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getShowById(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));
        return showMapper.toResponse(show);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getShowsByEvent(Long eventId, ShowStatus status, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", "id", eventId);
        }

        Page<Show> showPage = (status != null)
                ? showRepository.findByEventIdAndStatus(eventId, status, pageable)
                : showRepository.findByEventId(eventId, pageable);

        return showMapper.toPageResponse(showPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> getShowsByVenueAndDate(
            Long venueId,
            LocalDate date,
            ShowStatus status,
            Pageable pageable
    ) {
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.atTime(LocalTime.MAX);

        Page<Show> showPage = showRepository.findByVenueAndDateRange(
                venueId, rangeStart, rangeEnd, status, pageable
        );

        return showMapper.toPageResponse(showPage);
    }

    @Override
    @Transactional
    public ShowResponse updateShowStatus(Long showId, ShowStatusUpdateRequest request) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        show.setStatus(request.getStatus());
        Show updatedShow = showRepository.save(show);
        log.info("Updated Show ID: {} status to {}", showId, request.getStatus());
        return showMapper.toResponse(updatedShow);
    }

    @Override
    @Transactional
    public void cancelShow(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        if (show.getStatus() == ShowStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot cancel a show that is already completed");
        }

        show.setStatus(ShowStatus.CANCELLED);
        showRepository.save(show);
        log.warn("Show ID: {} has been CANCELLED", showId);
    }
}