package com.tickethub.booking.service.service.impl;

import com.tickethub.booking.service.dto.request.CreateEventRequest;
import com.tickethub.booking.service.dto.request.EventUpdateRequest;
import com.tickethub.booking.service.dto.response.EventResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.entity.Event;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import com.tickethub.booking.service.exception.InvalidStatusTransitionException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.EventMapper;
import com.tickethub.booking.service.repository.EventRepository;
import com.tickethub.booking.service.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        log.info("Creating new event with title: {}", request.getTitle());

        // Duplicate check rule: same title (case-insensitive), category, and release date
        if (eventRepository.existsByTitleIgnoreCaseAndCategoryAndReleaseDate(
                request.getTitle(),
                request.getCategory(),
                request.getReleaseDate())) {
            throw new InvalidStatusTransitionException("An event with this title, category, and release date already exists");
        }

        Event event = eventMapper.toEntity(request);
        Event saved = eventRepository.save(event);

        return eventMapper.toResponse(saved);
    }

    @Override
    public EventResponse getEventById(Long id) {
        log.debug("Fetching event with id: {}", id);
        return eventRepository.findById(id)
                .map(eventMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }

    @Override
    public PageResponse<EventResponse> getEvents(EventStatus status, EventCategory category, int page, int size) {
        log.debug("Fetching events page: {}, size: {}, status: {}, category: {}", page, size, status, category);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        Page<Event> eventPage = eventRepository.findByFilters(status, category, pageable);

        Page<EventResponse> responsePage = eventPage.map(eventMapper::toResponse);
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Long id, EventUpdateRequest request) {
        log.info("Updating event with id: {}", id);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        // Business rule 1: Do not allow direct modifications on archived events
        if (event.getStatus() == EventStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("Cannot update an archived event");
        }

        // Business rule 2: Prevent duplicate events across different IDs
        if (eventRepository.existsByTitleIgnoreCaseAndCategoryAndReleaseDateAndIdNot(
                request.getTitle(),
                request.getCategory(),
                request.getReleaseDate(),
                id)) {
            throw new InvalidStatusTransitionException("Another event with this title, category, and release date already exists");
        }

        eventMapper.updateEntity(event, request);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    public EventResponse changeStatus(Long id, EventStatus targetStatus) {
        log.info("Changing status of event id: {} to {}", id, targetStatus);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        // State Machine validation
        if (!event.getStatus().canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(event.getStatus(), targetStatus);
        }

        event.setStatus(targetStatus);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    public void archiveEvent(Long id) {
        log.info("Archiving event with id: {}", id);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

        if (!event.getStatus().canTransitionTo(EventStatus.ARCHIVED)) {
            throw new InvalidStatusTransitionException(event.getStatus(), EventStatus.ARCHIVED);
        }

        event.setStatus(EventStatus.ARCHIVED);
    }
}