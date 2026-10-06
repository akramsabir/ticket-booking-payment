package com.tickethub.booking.service.service;


import com.tickethub.booking.service.dto.request.CreateVenueRequest;
import com.tickethub.booking.service.dto.request.VenueUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.VenueResponse;
import com.tickethub.booking.service.entity.Venue;
import com.tickethub.booking.service.enums.VenueStatus;
import com.tickethub.booking.service.exception.DuplicateResourceException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.VenueMapper;
import com.tickethub.booking.service.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    @Override
    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        log.info("Creating venue name='{}' city='{}'", request.getName(), request.getCity());
        assertNotDuplicate(request.getName(), request.getCity(), null);

        Venue saved = venueRepository.save(venueMapper.toEntity(request));

        log.info("Created venue id={}", saved.getId());
        return venueMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(Long id) {
        log.debug("Fetching venue id={}", id);
        return venueMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> getVenues(VenueStatus status, String city, int page, int size) {
        log.debug("Listing venues status={} city={} page={} size={}", status, city, page, size);
        String cleanCity = (city == null || city.isBlank()) ? null : city.trim();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));

        return PageResponse.from(venueRepository.search(status, cleanCity, pageable).map(venueMapper::toResponse));
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(Long id, VenueUpdateRequest request) {
        log.info("Updating venue id={}", id);
        Venue venue = findOrThrow(id);
        assertNotDuplicate(request.getName(), request.getCity(), id);

        venueMapper.updateEntity(venue, request);
        Venue saved = venueRepository.saveAndFlush(venue);

        log.info("Updated venue id={}", id);
        return venueMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateVenue(Long id) {
        Venue venue = findOrThrow(id);
        if (venue.getStatus() == VenueStatus.INACTIVE) {
            log.debug("Venue id={} already inactive", id);
            return;
        }
        venue.setStatus(VenueStatus.INACTIVE);
        venueRepository.saveAndFlush(venue);
        log.info("Deactivated venue id={}", id);
    }

    private Venue findOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + id));
    }

    private void assertNotDuplicate(String name, String city, Long excludeId) {
        String cleanName = name.trim();
        String cleanCity = city.trim();
        boolean exists = (excludeId == null)
                ? venueRepository.existsByNameIgnoreCaseAndCityIgnoreCase(cleanName, cleanCity)
                : venueRepository.existsByNameIgnoreCaseAndCityIgnoreCaseAndIdNot(cleanName, cleanCity, excludeId);
        if (exists) {
            throw new DuplicateResourceException(
                    "A venue named '" + cleanName + "' already exists in " + cleanCity);
        }
    }
}
