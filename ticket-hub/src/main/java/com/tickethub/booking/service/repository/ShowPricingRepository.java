package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.dto.request.UpdateTierPriceRequest;
import com.tickethub.booking.service.dto.response.ShowPricingResponse;
import com.tickethub.booking.service.entity.ShowPricing;
import com.tickethub.booking.service.enums.SeatTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShowPricingRepository extends JpaRepository<ShowPricing, Long> {

    List<ShowPricing> findByShowId(Long showId);

    Optional<ShowPricing> findByShowIdAndSeatTier(Long showId, SeatTier seatTier);

    boolean existsByShowIdAndSeatTier(Long showId, SeatTier seatTier);

    void deleteByShowId(Long showId);

}