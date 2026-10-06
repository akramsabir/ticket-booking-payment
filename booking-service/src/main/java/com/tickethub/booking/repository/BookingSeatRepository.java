package com.tickethub.booking.repository;

import com.tickethub.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    boolean existsByShowIdAndSeatId(Long showId, String seatId);

    List<BookingSeat> findAllByShowIdAndSeatIdIn(Long showId, List<String> seatIds);
}
