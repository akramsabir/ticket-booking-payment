package com.tickethub.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Local lookup "userId -> email", learned from payment events (they carry userId + customerEmail).
 * Booking events only carry customerId, so this lets us still find the address for booking emails
 * WITHOUT calling another service or reading another service's database.
 */
@Entity
@Table(name = "user_contacts")
public class UserContact {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserContact() {
    }

    public UserContact(Long userId, String email) {
        this.userId = userId;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; this.updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
