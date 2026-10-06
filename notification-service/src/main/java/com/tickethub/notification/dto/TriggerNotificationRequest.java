package com.tickethub.notification.dto;

import com.tickethub.notification.entity.NotificationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of POST /api/notifications/trigger - ask for one of the ready-made emails over HTTP
 * (use this from booking-service when Kafka is switched off, or to enrich the mail with show/seat details).
 */
public class TriggerNotificationRequest {

    @NotNull(message = "type is required (BOOKING_CONFIRMED, BOOKING_CANCELLED, BOOKING_EXPIRED, PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED)")
    private NotificationType type;

    @NotBlank(message = "recipientEmail is required")
    @Email(message = "recipientEmail must be a valid email address")
    @Size(max = 254)
    private String recipientEmail;

    @Size(max = 150)
    private String recipientName;

    @NotNull(message = "bookingId is required")
    private Long bookingId;

    private Long paymentId;
    private Long userId;

    @DecimalMin(value = "0.00", message = "amount cannot be negative")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter code like INR")
    private String currency;

    @Size(max = 300)
    private String reason;

    @Size(max = 200)
    private String showTitle;

    @Size(max = 200)
    private String venue;

    @Size(max = 100)
    private String showTime;

    @Size(max = 10, message = "at most 10 seats")
    private List<@Size(max = 20) String> seats;

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getShowTitle() { return showTitle; }
    public void setShowTitle(String showTitle) { this.showTitle = showTitle; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getShowTime() { return showTime; }
    public void setShowTime(String showTime) { this.showTime = showTime; }
    public List<String> getSeats() { return seats; }
    public void setSeats(List<String> seats) { this.seats = seats; }
}
