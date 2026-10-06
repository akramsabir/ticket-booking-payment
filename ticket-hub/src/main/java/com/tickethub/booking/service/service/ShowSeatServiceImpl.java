package com.tickethub.booking.service.service;

import com.tickethub.booking.service.dto.response.ShowSeatResponse;
import com.tickethub.booking.service.entity.Seat;
import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.entity.ShowPricing;
import com.tickethub.booking.service.entity.ShowSeat;
import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import com.tickethub.booking.service.enums.ShowSeatStatus;
import com.tickethub.booking.service.exception.BusinessRuleException;
import com.tickethub.booking.service.exception.ResourceNotFoundException;
import com.tickethub.booking.service.mapper.ShowSeatMapper;
import com.tickethub.booking.service.repository.SeatRepository;
import com.tickethub.booking.service.repository.ShowPricingRepository;
import com.tickethub.booking.service.repository.ShowRepository;
import com.tickethub.booking.service.repository.ShowSeatRepository;
import com.tickethub.booking.service.service.ShowSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowSeatServiceImpl implements ShowSeatService {

    private final ShowSeatRepository showSeatRepository;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ShowPricingRepository showPricingRepository;
    private final ShowSeatMapper showSeatMapper;

    @Override
    @Transactional
    public List<ShowSeatResponse> generateShowSeats(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        // 1. Fetch configured pricing for this show
        List<ShowPricing> pricings = showPricingRepository.findByShowId(showId);
        if (pricings.isEmpty()) {
            throw new BusinessRuleException("Cannot generate seats: No pricing configured for show id " + showId);
        }

        Map<SeatTier, BigDecimal> priceMap = pricings.stream()
                .collect(Collectors.toMap(ShowPricing::getSeatTier, ShowPricing::getPrice));

        // 2. Fetch all physical active seats in the hall (unpaged internal batch)
        Long hallId = show.getHall().getId();
        List<Seat> activeSeats = seatRepository.findByHallIdAndStatus(hallId, SeatStatus.AVAILABLE);
        if (activeSeats.isEmpty()) {
            throw new BusinessRuleException("No available seats found in hall id " + hallId);
        }

        // 3. Clear existing show seats if regenerating
        showSeatRepository.deleteByShowId(showId);
        showSeatRepository.flush();

        // 4. Map active seats to ShowSeat entities via ShowSeatMapper
        List<ShowSeat> showSeats = activeSeats.stream()
                .map(seat -> {
                    BigDecimal price = priceMap.get(seat.getTier());
                    if (price == null) {
                        throw new BusinessRuleException("Missing pricing for seat tier: " + seat.getTier());
                    }
                    return showSeatMapper.toEntity(show, seat, price, ShowSeatStatus.AVAILABLE);
                })
                .toList();

        List<ShowSeat> saved = showSeatRepository.saveAll(showSeats);

        // 5. Return DTO response list via ShowSeatMapper
        return showSeatMapper.toResponseList(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowSeatResponse> getShowSeats(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show", "id", showId);
        }

        List<ShowSeat> showSeats = showSeatRepository.findByShowIdWithSeatDetails(showId);
        return showSeatMapper.toResponseList(showSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowSeatResponse> getShowSeats(Long showId, Pageable pageable) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show", "id", showId);
        }

        Page<ShowSeat> showSeatsPage = showSeatRepository.findByShowIdWithSeatDetails(showId, pageable);
        return showSeatsPage.map(showSeatMapper::toResponse);
    }
    @Override
    @Transactional
    public void markSeatsAsBooked(Long showId, List<String> seatIds) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show", "id", showId);
        }

        List<ShowSeat> showSeats = showSeatRepository.findByShowIdWithSeatDetails(showId);

        List<ShowSeat> toUpdate = showSeats.stream()
                .filter(ss -> {
                    String idStr = String.valueOf(ss.getId());
                    String seatIdStr = ss.getSeat() != null ? String.valueOf(ss.getSeat().getId()) : null;
                    String seatNumber = ss.getSeat() != null ? String.valueOf(ss.getSeat().getSeatNumber()) : null;

                    return seatIds.contains(idStr)
                            || (seatIdStr != null && seatIds.contains(seatIdStr))
                            || (seatNumber != null && seatIds.contains(seatNumber));
                })
                .toList();

        for (ShowSeat ss : toUpdate) {
            ss.setStatus(ShowSeatStatus.BOOKED);
        }

        showSeatRepository.saveAll(toUpdate);
    }
}