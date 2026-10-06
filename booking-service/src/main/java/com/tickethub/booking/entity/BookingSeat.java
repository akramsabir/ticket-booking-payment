package com.tickethub.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;


/**
 * One row per (show, seat) that has been booked.
 * The unique constraint on (show_id, seat_id) is the actual mechanism
 * that prevents two customers from booking the same seat for the same show -
 * enforced by the database, not just application logic, so it holds even
 * under concurrent requests.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "booking_seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_show_seat",
                columnNames = {"show_id", "seat_id"}
        )
)
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private String seatId;

    /**
     * Frozen at booking time from event-service's response - NOT re-fetched
     * later. If event-service's pricing changes afterward, this booking's
     * historical price must not change with it.
     */
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    /** What the customer sees on the seat, e.g. "A5" (row + seat number). seatId above is only an internal reference. */
    @Column(name = "seat_label", length = 30)
    private String seatLabel;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
}
