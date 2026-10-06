package com.tickethub.booking.repository;

import com.tickethub.booking.entity.BookingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingHistoryRepository extends JpaRepository<BookingHistory, Long> {
    List<BookingHistory> findByBookingIdOrderByChangedAtAsc(Long bookingId);
}
