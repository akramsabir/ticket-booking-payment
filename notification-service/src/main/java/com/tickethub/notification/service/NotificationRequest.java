package com.tickethub.notification.service;

import com.tickethub.notification.entity.NotificationType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Everything needed to create one notification. Built by the Kafka handler or the REST controller. */
public class NotificationRequest {

    private NotificationType type;
    private String dedupeKey;
    private String recipient;
    private String recipientName;
    private String subject;        // only used for CUSTOM
    private String message;        // only used for CUSTOM (plain text)
    private Long bookingId;
    private Long paymentId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String showTitle;
    private String venue;
    private String showTime;
    private List<String> seats = new ArrayList<>();

    public NotificationRequest(NotificationType type, String dedupeKey) {
        this.type = type;
        this.dedupeKey = dedupeKey;
    }

    public NotificationType getType() { return type; }
    public String getDedupeKey() { return dedupeKey; }
    public String getRecipient() { return recipient; }
    public String getRecipientName() { return recipientName; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public Long getBookingId() { return bookingId; }
    public Long getPaymentId() { return paymentId; }
    public Long getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public String getShowTitle() { return showTitle; }
    public String getVenue() { return venue; }
    public String getShowTime() { return showTime; }
    public List<String> getSeats() { return seats; }

    public NotificationRequest recipient(String v) { this.recipient = v; return this; }
    public NotificationRequest recipientName(String v) { this.recipientName = v; return this; }
    public NotificationRequest subject(String v) { this.subject = v; return this; }
    public NotificationRequest message(String v) { this.message = v; return this; }
    public NotificationRequest bookingId(Long v) { this.bookingId = v; return this; }
    public NotificationRequest paymentId(Long v) { this.paymentId = v; return this; }
    public NotificationRequest userId(Long v) { this.userId = v; return this; }
    public NotificationRequest amount(BigDecimal v) { this.amount = v; return this; }
    public NotificationRequest currency(String v) { this.currency = v; return this; }
    public NotificationRequest reason(String v) { this.reason = v; return this; }
    public NotificationRequest showTitle(String v) { this.showTitle = v; return this; }
    public NotificationRequest venue(String v) { this.venue = v; return this; }
    public NotificationRequest showTime(String v) { this.showTime = v; return this; }
    public NotificationRequest seats(List<String> v) { this.seats = v == null ? new ArrayList<>() : new ArrayList<>(v); return this; }
}
