package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.entity.Venue;
import com.tickethub.booking.service.enums.VenueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    // A null filter means "don't filter on this". City matching is case-insensitive because MySQL's
    // default collation is; if you change the collation, revisit this query.
    @Query("select v from Venue v "
            + "where (:status is null or v.status = :status) "
            + "and (:city is null or v.city = :city)")
    Page<Venue> search(@Param("status") VenueStatus status,
                       @Param("city") String city,
                       Pageable pageable);

    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);

    boolean existsByNameIgnoreCaseAndCityIgnoreCaseAndIdNot(String name, String city, Long id);
}
