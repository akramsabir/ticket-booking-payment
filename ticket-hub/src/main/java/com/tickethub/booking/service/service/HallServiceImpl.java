package com.tickethub.booking.service.service;


import com.tickethub.booking.service.dto.request.HallCreateRequest;
import com.tickethub.booking.service.dto.request.HallUpdateRequest;
import com.tickethub.booking.service.dto.response.HallResponse;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.entity.Hall;
import com.tickethub.booking.service.entity.Venue;
import com.tickethub.booking.service.enums.HallStatus;
import com.tickethub.booking.service.enums.VenueStatus;
import com.tickethub.booking.service.exception.BusinessRuleException;
import com.tickethub.booking.service.exception.DuplicateResourceException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.HallMapper;
import com.tickethub.booking.service.repository.HallRepository;
import com.tickethub.booking.service.repository.VenueRepository;
import com.tickethub.booking.service.service.HallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HallServiceImpl implements HallService {

    private final HallRepository hallRepository;
    private final VenueRepository venueRepository;
    private final HallMapper hallMapper;

    @Override
    @Transactional
    public HallResponse createHall(Long venueId, HallCreateRequest request) {
        log.info("Creating hall '{}' for venueId: {}", request.name(), venueId);

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + venueId));

        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new BusinessRuleException("Cannot add a hall to an INACTIVE venue");
        }

        if (hallRepository.existsByVenueIdAndNameIgnoreCase(venueId, request.name().trim())) {
            throw new DuplicateResourceException(
                    "Hall with name '" + request.name().trim() + "' already exists in this venue"
            );
        }

        Hall hall = hallMapper.toEntity(request, venue);
        Hall savedHall = hallRepository.save(hall);

        return hallMapper.toResponse(savedHall);
    }

    @Override
    @Transactional
    public HallResponse updateHall(Long hallId, HallUpdateRequest request) {
        log.info("Updating hall with id: {}", hallId);

        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + hallId));

        Long venueId = hall.getVenue().getId();
        if (hallRepository.existsByVenueIdAndNameIgnoreCaseAndIdNot(venueId, request.name().trim(), hallId)) {
            throw new DuplicateResourceException(
                    "Another hall with name '" + request.name().trim() + "' already exists in this venue"
            );
        }

        hallMapper.updateEntity(hall, request);
        Hall updatedHall = hallRepository.save(hall);

        return hallMapper.toResponse(updatedHall);
    }

    @Override
    @Transactional(readOnly = true)
    public HallResponse getHallById(Long hallId) {
        Hall hall = hallRepository.findByIdWithVenue(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + hallId));
        return hallMapper.toResponse(hall);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HallResponse> getHallsByVenue(Long venueId, HallStatus status, Pageable pageable) {
        if (!venueRepository.existsById(venueId)) {
            throw new ResourceNotFoundException("Venue not found with id: " + venueId);
        }

        Page<Hall> hallPage = hallRepository.findByVenueIdAndStatus(venueId, status, pageable);
        return PageResponse.from(hallPage.map(hallMapper::toResponse));
    }

    @Override
    @Transactional
    public void deleteHall(Long hallId) {
        log.info("Deleting hall with id: {}", hallId);
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + hallId));

        // Soft-delete pattern: update status to INACTIVE
        hall.setStatus(HallStatus.INACTIVE);
        hallRepository.save(hall);
    }
}