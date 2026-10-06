package com.tickethub.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A receipt for every Razorpay webhook we have processed.
 *
 * WHY: Razorpay may deliver the SAME event more than once (blueprint scenario SC-10). Each delivery carries a
 * unique "X-Razorpay-Event-Id" header. We store that id (unique!) in the SAME database transaction as the work
 * the event caused. So either both the work and the receipt are saved, or neither is - which means a half-processed
 * event is retried by Razorpay, and a fully processed one is ignored the second time.
 *
 * We deliberately do NOT store the payload: it contains customer details we do not need to keep.
 */
@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_webhook_event_id", columnNames = "event_id"))
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public WebhookEvent() {
    }

    public WebhookEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getReceivedAt() { return receivedAt; }
}
