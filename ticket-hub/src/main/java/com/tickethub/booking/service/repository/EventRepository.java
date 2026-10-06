package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.entity.Event;
import com.tickethub.booking.service.enums.EventCategory;
import com.tickethub.booking.service.enums.EventStatus;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e "
            + "WHERE (:status IS NULL OR e.status = :status) "
            + "AND (:category IS NULL OR e.category = :category)")
    Page<Event> findByFilters(@Param("status") EventStatus status,
                              @Param("category") EventCategory category,
                              Pageable pageable);

    // Duplicate rule: same title (ignoring case), category and release date is the same event.
    boolean existsByTitleIgnoreCaseAndCategoryAndReleaseDate(String title, EventCategory category, LocalDate releaseDate);

    boolean existsByTitleIgnoreCaseAndCategoryAndReleaseDateAndIdNot(String title, EventCategory category,
                                                                     LocalDate releaseDate, Long id);
}