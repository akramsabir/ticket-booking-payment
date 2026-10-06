package com.tickethub.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One row per notification. This service OWNS this table (blueprint: "Notification Service owns
 * notification delivery state"). The rendered subject/body are stored so a retry sends exactly the same mail.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_notif_booking", columnList = "booking_id"),
        @Index(name = "idx_notif_recipient", columnList = "recipient")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notif_public_id", columnNames = "notification_id"),
        @UniqueConstraint(name = "uk_notif_dedupe", columnNames = "dedupe_key")
})
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public id (UUID) returned by the API. */
    @Column(name = "notification_id", nullable = false, updatable = false, length = 36)
    private String notificationId;

    /** Same dedupe key => same notification => never sent twice (handles duplicate Kafka delivery). */
    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 150)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType channel = NotificationChannelType.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    @Column(length = 254)
    private String recipient;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(length = 255)
    private String subject;

    @Lob
    @Column(name = "html_body", columnDefinition = "LONGTEXT")
    private String htmlBody;

    @Lob
    @Column(name = "text_body", columnDefinition = "LONGTEXT")
    private String textBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** Message-Id returned by the mail server for the successful attempt. */
    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** When the retry job may pick this notification up again. null = nothing scheduled. */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public NotificationChannelType getChannel() { return channel; }
    public void setChannel(NotificationChannelType channel) { this.channel = channel; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
}
