package com.tickethub.booking.service.repository;

import com.tickethub.booking.service.entity.ShowSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {


    // 2. Add the paginated method for page-by-page retrieval
    @Query(value = "SELECT ss FROM ShowSeat ss " +
            "JOIN FETCH ss.seat " +
            "WHERE ss.show.id = :showId " +
            "ORDER BY ss.seat.rowLabel ASC, ss.seat.seatNumber ASC",
            countQuery = "SELECT count(ss) FROM ShowSeat ss WHERE ss.show.id = :showId")
    Page<ShowSeat> findByShowIdWithSeatDetails(@Param("showId") Long showId, Pageable pageable);

    @Query("SELECT ss FROM ShowSeat ss " +
            "JOIN FETCH ss.seat " +
            "WHERE ss.show.id = :showId " +
            "ORDER BY ss.seat.rowLabel ASC, ss.seat.seatNumber ASC")
    List<ShowSeat> findByShowIdWithSeatDetails(@Param("showId") Long showId);

    boolean existsByShowId(Long showId);

    void deleteByShowId(Long showId);
}