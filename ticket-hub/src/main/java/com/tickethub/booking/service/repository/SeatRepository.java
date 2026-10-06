package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.dto.response.SeatResponse;
import com.tickethub.booking.service.entity.Seat;
import com.tickethub.booking.service.enums.SeatStatus;
import com.tickethub.booking.service.enums.SeatTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    // Retrieval with pagination
    Page<Seat> findByHallId(Long hallId, Pageable pageable);

    // Filter by status with pagination
    Page<Seat> findByHallIdAndStatus(Long hallId, SeatStatus status, Pageable pageable);

    // Filter by tier with pagination
    Page<Seat> findByHallIdAndTier(Long hallId, SeatTier tier, Pageable pageable);

    // Filter by both tier and status with pagination (useful composite filter)
    Page<Seat> findByHallIdAndTierAndStatus(Long hallId, SeatTier tier, SeatStatus status, Pageable pageable);

    // Seat lookup by natural key inside a hall
    Optional<Seat> findByHallIdAndSeatCode(Long hallId, String seatCode);

    // Verification checks
    boolean existsByHallId(Long hallId);

    boolean existsByHallIdAndSeatCode(Long hallId, String seatCode);

    long countByHallId(Long hallId);

    // For internal show seat generation (must insert all seats for the show)
    List<Seat> findByHallIdAndStatus(Long hallId, SeatStatus status);

    // Bulk delete with cache invalidation and immediate flush
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM seats WHERE hall_id = :hallId", nativeQuery = true)
    void deleteByHallId(@Param("hallId") Long hallId);
}