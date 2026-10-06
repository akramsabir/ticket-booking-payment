package com.tickethub.booking.repository;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);
    Page<Booking> findByShowId(Long showId, Pageable pageable);
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime time);

    // Payment reconciliation: bookings still inside their payment window (oldest first, a small batch per run)
    List<Booking> findTop50ByStatusAndExpiresAtAfterOrderByCreatedAtAsc(BookingStatus status, LocalDateTime time);
}
