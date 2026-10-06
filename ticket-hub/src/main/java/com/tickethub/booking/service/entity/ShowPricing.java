package com.tickethub.booking.service.entity;

import com.tickethub.booking.service.enums.SeatTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "show_pricings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_show_seat_tier", columnNames = {"show_id", "seat_tier"})
        },
        indexes = {
                @Index(name = "idx_show_pricing_show_id", columnList = "show_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowPricing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false, foreignKey = @ForeignKey(name = "fk_show_pricing_show"))
    private Show show;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_tier", nullable = false, length = 30)
    private SeatTier seatTier;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";
}