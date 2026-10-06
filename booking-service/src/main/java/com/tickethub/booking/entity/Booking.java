package com.tickethub.booking.entity;

import com.tickethub.booking.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_customer", columnList = "customer_id"),
        @Index(name = "idx_booking_show", columnList = "show_id"),
        @Index(name = "idx_booking_status", columnList = "status"),
        @Index(name = "idx_booking_expires_at", columnList = "expires_at")
})
public class Booking extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    /**
     * Where the confirmation email goes. Taken from the logged-in user's token when the booking is created and stored
     * HERE, because a booking is often confirmed later by a background job or by an admin, when the customer's own
     * login is not available any more.
     */
    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    /**
     * A snapshot of what was bought, frozen at booking time (like the seat prices). It is only used to make the
     * confirmation email readable; it is best-effort and may be empty if event-service could not be asked.
     */
    @Column(name = "show_title", length = 200)
    private String showTitle;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    /** Already formatted for humans, e.g. "Sat 18 Oct 2026, 19:30". */
    @Column(name = "show_time", length = 60)
    private String showTime;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingSeat> seats = new ArrayList<>();

    public void addSeat(BookingSeat seat) {
        seats.add(seat);
        seat.setBooking(this);
    }
}
