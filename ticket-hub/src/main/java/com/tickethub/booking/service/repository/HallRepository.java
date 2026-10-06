package com.tickethub.booking.service.repository;


import com.tickethub.booking.service.entity.Hall;
import com.tickethub.booking.service.enums.HallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HallRepository extends JpaRepository<Hall, Long> {

    // Duplicate check on create: Screen name must be unique within that specific venue
    boolean existsByVenueIdAndNameIgnoreCase(Long venueId, String name);

    // Duplicate check on update: Exclude the current hall ID
    boolean existsByVenueIdAndNameIgnoreCaseAndIdNot(Long venueId, String name, Long id);

    // Query halls by venue with optional status filter and pagination
    @Query("SELECT h FROM Hall h WHERE h.venue.id = :venueId " +
            "AND (:status IS NULL OR h.status = :status)")
    Page<Hall> findByVenueIdAndStatus(
            @Param("venueId") Long venueId,
            @Param("status") HallStatus status,
            Pageable pageable
    );

    // Fetch hall along with venue details in a single query when needed
    @Query("SELECT h FROM Hall h JOIN FETCH h.venue WHERE h.id = :id")
    Optional<Hall> findByIdWithVenue(@Param("id") Long id);
}
