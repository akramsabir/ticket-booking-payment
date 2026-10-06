package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.entity.Show;
import com.tickethub.booking.service.enums.ShowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    // Overlap query: checks if another show in the same hall collides with [startTime, endTime]
    @Query("""
        SELECT COUNT(s) > 0 FROM Show s
        WHERE s.hall.id = :hallId
          AND s.status != 'CANCELLED'
          AND (:showId IS NULL OR s.id != :showId)
          AND s.startTime < :endTime
          AND s.endTime > :startTime
    """)
    boolean hasOverlappingShow(
            @Param("hallId") Long hallId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("showId") Long showId
    );

    // Filter shows for an event with pagination
    Page<Show> findByEventIdAndStatus(Long eventId, ShowStatus status, Pageable pageable);

    Page<Show> findByEventId(Long eventId, Pageable pageable);

    // Filter shows for a hall with pagination
    Page<Show> findByHallId(Long hallId, Pageable pageable);

    // Find shows scheduled on a particular date range for a venue
    @Query("""
        SELECT s FROM Show s
        WHERE s.hall.venue.id = :venueId
          AND s.startTime >= :rangeStart
          AND s.startTime < :rangeEnd
          AND (:status IS NULL OR s.status = :status)
    """)
    Page<Show> findByVenueAndDateRange(
            @Param("venueId") Long venueId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("status") ShowStatus status,
            Pageable pageable
    );
}