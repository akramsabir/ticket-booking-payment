package com.tickethub.notification.dto;

import com.tickethub.notification.entity.NotificationChannelType;
import com.tickethub.notification.entity.NotificationRecord;
import com.tickethub.notification.entity.NotificationStatus;
import com.tickethub.notification.entity.NotificationType;

import java.time.Instant;

/** What the API returns for a notification (the stored email body is not included; use /preview). */
public class NotificationResponse {

    private String notificationId;
    private NotificationChannelType channel;
    private NotificationType type;
    private NotificationStatus status;
    private String recipient;
    private String subject;
    private Long bookingId;
    private Long paymentId;
    private Long userId;
    private int attempts;
    private String lastError;
    private String providerReference;
    private Instant createdAt;
    private Instant sentAt;
    private Instant nextRetryAt;
    private Boolean duplicate;

    public static NotificationResponse from(NotificationRecord n) {
        NotificationResponse r = new NotificationResponse();
        r.notificationId = n.getNotificationId();
        r.channel = n.getChannel();
        r.type = n.getType();
        r.status = n.getStatus();
        r.recipient = n.getRecipient();
        r.subject = n.getSubject();
        r.bookingId = n.getBookingId();
        r.paymentId = n.getPaymentId();
        r.userId = n.getUserId();
        r.attempts = n.getAttempts();
        r.lastError = n.getLastError();
        r.providerReference = n.getProviderReference();
        r.createdAt = n.getCreatedAt();
        r.sentAt = n.getSentAt();
        r.nextRetryAt = n.getNextRetryAt();
        return r;
    }

    public static NotificationResponse from(NotificationRecord n, boolean duplicate) {
        NotificationResponse r = from(n);
        r.duplicate = duplicate ? Boolean.TRUE : null;
        return r;
    }

    public String getNotificationId() { return notificationId; }
    public NotificationChannelType getChannel() { return channel; }
    public NotificationType getType() { return type; }
    public NotificationStatus getStatus() { return status; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public Long getBookingId() { return bookingId; }
    public Long getPaymentId() { return paymentId; }
    public Long getUserId() { return userId; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getProviderReference() { return providerReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Boolean getDuplicate() { return duplicate; }
}
