package com.tickethub.booking.service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.service.dto.request.SeatStatusUpdateRequest;
import com.tickethub.booking.service.dto.response.PageResponse;
import com.tickethub.booking.service.dto.response.SeatResponse;
import com.tickethub.booking.service.entity.Hall;
import com.tickethub.booking.service.entity.Seat;
import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.exception.DuplicateResourceException;
import com.tickethub.booking.service.exception.InvalidJsonDataException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.SeatMapper;
import com.tickethub.booking.service.repository.HallRepository;
import com.tickethub.booking.service.repository.SeatRepository;
import com.tickethub.booking.service.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final HallRepository hallRepository;
    private final SeatMapper seatMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public List<SeatResponse> generateSeatsFromHallLayout(Long hallId) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall", "id", hallId));

        if (seatRepository.existsByHallId(hallId)) {
            throw new DuplicateResourceException(
                    "Seats have already been generated for Hall ID: " + hallId + ". Use regenerate if reconfiguring."
            );
        }

        List<Seat> generatedSeats = buildSeatsFromLayout(hall);
        List<Seat> savedSeats = seatRepository.saveAll(generatedSeats);
        log.info("Successfully generated {} seats for Hall ID: {}", savedSeats.size(), hallId);

        return seatMapper.toResponseList(savedSeats);
    }



    @Override
    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> getSeatsByHall(Long hallId, SeatStatus status, SeatTier tier, Pageable pageable) {
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall", "id", hallId);
        }

        Page<Seat> seatPage;
        if (status != null && tier != null) {
            seatPage = seatRepository.findByHallIdAndTierAndStatus(hallId, tier, status, pageable);
        } else if (status != null) {
            seatPage = seatRepository.findByHallIdAndStatus(hallId, status, pageable);
        } else if (tier != null) {
            seatPage = seatRepository.findByHallIdAndTier(hallId, tier, pageable);
        } else {
            seatPage = seatRepository.findByHallId(hallId, pageable);
        }

        return seatMapper.toPageResponse(seatPage);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatResponse getSeatById(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", seatId));
        return seatMapper.toResponse(seat);
    }

    @Override
    @Transactional
    public SeatResponse updateSeatStatus(Long seatId, SeatStatusUpdateRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", seatId));

        seat.setStatus(request.getStatus());
        Seat updatedSeat = seatRepository.save(seat);
        log.info("Updated status of Seat ID: {} to {}", seatId, request.getStatus());
        return seatMapper.toResponse(updatedSeat);
    }

    @Override
    @Transactional
    public void regenerateSeatsForHall(Long hallId) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall", "id", hallId));

        // 1. Delete old seats and flush changes immediately to database
        seatRepository.deleteByHallId(hallId);
        seatRepository.flush();

        // 2. Build fresh seat list from the hall's layout JSON
        List<Seat> regeneratedSeats = buildSeatsFromLayout(hall);

        // 3. Persist new seats
        seatRepository.saveAll(regeneratedSeats);
        log.info("Regenerated exactly {} seats for Hall ID: {}", regeneratedSeats.size(), hallId);
    }

    private List<Seat> buildSeatsFromLayout(Hall hall) {
        if (hall.getLayoutJson() == null || hall.getLayoutJson().isBlank()) {
            throw new InvalidJsonDataException("Hall ID " + hall.getId() + " does not contain any layout configuration.");
        }

        try {
            JsonNode layoutNode = objectMapper.readTree(hall.getLayoutJson());
            int columns = layoutNode.path("columns").asInt();
            JsonNode sectionsNode = layoutNode.path("sections");

            if (columns <= 0 || !sectionsNode.isArray() || sectionsNode.isEmpty()) {
                throw new InvalidJsonDataException("Invalid layout format for Hall ID: " + hall.getId());
            }

            Map<String, SeatTier> rowTierMap = new LinkedHashMap<>();
            for (JsonNode section : sectionsNode) {
                String tierStr = section.path("tier").asText().toUpperCase();
                SeatTier tier;
                try {
                    tier = SeatTier.valueOf(tierStr);
                } catch (IllegalArgumentException ex) {
                    tier = SeatTier.CLASSIC;
                }

                JsonNode rowsNode = section.path("rows");
                if (rowsNode.isArray()) {
                    for (JsonNode row : rowsNode) {
                        rowTierMap.put(row.asText(), tier);
                    }
                }
            }

            List<Seat> seats = new ArrayList<>();
            for (Map.Entry<String, SeatTier> entry : rowTierMap.entrySet()) {
                String rowLabel = entry.getKey();
                SeatTier tier = entry.getValue();

                for (int col = 1; col <= columns; col++) {
                    Seat seat = Seat.builder()
                            .hall(hall)
                            .rowLabel(rowLabel)
                            .seatNumber(col)
                            .seatCode(rowLabel + col)
                            .tier(tier)
                            .status(SeatStatus.AVAILABLE)
                            .build();
                    seats.add(seat);
                }
            }
            return seats;
        } catch (InvalidJsonDataException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidJsonDataException("Failed to parse hall layout JSON for Hall ID: " + hall.getId() + ". " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SeatResponse getSeatByHallAndSeatCode(Long hallId, String seatCode) {
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall", "id", hallId);
        }

        Seat seat = seatRepository.findByHallIdAndSeatCode(hallId, seatCode.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Seat '%s' not found in Hall ID: %d", seatCode, hallId)
                ));

        return seatMapper.toResponse(seat);
    }

}