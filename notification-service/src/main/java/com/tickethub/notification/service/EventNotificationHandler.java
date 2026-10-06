package com.tickethub.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.notification.entity.NotificationType;
import com.tickethub.notification.exception.InvalidEventException;
import com.tickethub.notification.exception.NotificationDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Understands the two Kafka message formats and turns them into notifications.
 * Kept separate from the @KafkaListener classes so it can be tested without Kafka.
 *
 * payment-events (published by payment-service, see PaymentEvent.java there):
 *   { eventType: PAYMENT_SUCCESS|PAYMENT_FAILED|PAYMENT_REFUNDED|PAYMENT_INITIATED, paymentId, bookingId, userId,
 *     customerEmail, amount, currency, status, failureReason, occurredAt }
 *
 * booking-events (published by booking-service, see BookingEventPublisher.java there):
 *   { bookingId, customerId, showId, status: PENDING|CONFIRMED|CANCELLED|EXPIRED, totalAmount,
 *     [customerEmail, showTitle, venue, showTime, seats: ["A5","A6"]] }   (the bracketed fields are optional)
 */
@Service
public class EventNotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(EventNotificationHandler.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserContactService contacts;

    public EventNotificationHandler(ObjectMapper objectMapper,
                                    NotificationService notificationService,
                                    UserContactService contacts) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.contacts = contacts;
    }

    // ------------------------------------------------------------------ payment-events

    public void handlePaymentEvent(String payload) {
        JsonNode json = parse(payload);

        String eventType = text(json, "eventType");
        Long bookingId = longValue(json, "bookingId");
        Long paymentId = longValue(json, "paymentId");
        Long userId = longValue(json, "userId");
        String email = text(json, "customerEmail");

        // Learn the address even for events we do not email about (e.g. PAYMENT_INITIATED).
        contacts.remember(userId, email);

        NotificationType type = paymentType(eventType);
        if (type == null) {
            log.debug("Ignoring payment event of type {}", eventType);
            return;
        }
        if (bookingId == null && paymentId == null) {
            throw new InvalidEventException("Payment event has neither bookingId nor paymentId");
        }
        if (!notificationService.isEnabled(type)) {
            log.debug("{} notifications are disabled, skipping", type);
            return;
        }

        String reference = String.valueOf(paymentId != null ? paymentId : bookingId);
        NotificationRequest request = new NotificationRequest(type, "PAYMENT:" + eventType + ":" + reference)
                .recipient(email)
                .bookingId(bookingId)
                .paymentId(paymentId)
                .userId(userId)
                .amount(decimal(json, "amount"))
                .currency(text(json, "currency"));
        if (type == NotificationType.PAYMENT_FAILED) {
            request.reason(text(json, "failureReason"));
        }
        submitQuietly(request);
    }

    // ------------------------------------------------------------------ booking-events

    public void handleBookingEvent(String payload) {
        JsonNode json = parse(payload);

        Long bookingId = longValue(json, "bookingId");
        Long customerId = longValue(json, "customerId");
        String status = text(json, "status");
        if (bookingId == null) {
            throw new InvalidEventException("Booking event has no bookingId");
        }

        NotificationType type = bookingType(status);
        if (type == null) {
            log.debug("Ignoring booking event with status {} (booking {})", status, bookingId);
            return;
        }
        if (!notificationService.isEnabled(type)) {
            log.debug("{} notifications are disabled, skipping", type);
            return;
        }

        String email = text(json, "customerEmail");
        if (email == null) {
            Optional<String> known = contacts.findEmail(customerId);
            email = known.orElse(null);
        } else {
            contacts.remember(customerId, email);
        }

        NotificationRequest request = new NotificationRequest(type, "BOOKING:" + status.toUpperCase() + ":" + bookingId)
                .recipient(email)
                .bookingId(bookingId)
                .userId(customerId)
                .amount(decimal(json, "totalAmount"))
                .currency("INR");
        if (type == NotificationType.BOOKING_CANCELLED) {
            request.reason(text(json, "reason"));
        }
        // What the customer bought. Optional: booking-service sends these when it knows them, and the email simply
        // leaves the rows out when it does not (the renderer skips blank values).
        request.showTitle(text(json, "showTitle"))
                .venue(text(json, "venue"))
                .showTime(text(json, "showTime"))
                .seats(textList(json, "seats"));
        submitQuietly(request);
    }

    // ------------------------------------------------------------------ helpers

    private void submitQuietly(NotificationRequest request) {
        try {
            SubmitResult result = notificationService.submit(request);
            log.info("Notification {} for booking {} -> {} ({})", request.getType(), request.getBookingId(),
                    result.record().getStatus(), result.created() ? "new" : "duplicate");
        } catch (NotificationDisabledException ex) {
            log.debug(ex.getMessage());
        }
        // Any other exception (e.g. the database is down) is rethrown on purpose: Kafka then retries the
        // message and finally moves it to the dead-letter topic, so nothing is silently lost.
    }

    static NotificationType paymentType(String eventType) {
        if (eventType == null) return null;
        return switch (eventType.toUpperCase()) {
            case "PAYMENT_SUCCESS" -> NotificationType.PAYMENT_SUCCESS;
            case "PAYMENT_FAILED" -> NotificationType.PAYMENT_FAILED;
            case "PAYMENT_REFUNDED" -> NotificationType.PAYMENT_REFUNDED;
            default -> null;
        };
    }

    static NotificationType bookingType(String status) {
        if (status == null) return null;
        return switch (status.toUpperCase()) {
            case "CONFIRMED" -> NotificationType.BOOKING_CONFIRMED;
            case "CANCELLED" -> NotificationType.BOOKING_CANCELLED;
            case "EXPIRED" -> NotificationType.BOOKING_EXPIRED;
            default -> null;
        };
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventException("Empty Kafka message");
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new InvalidEventException("Kafka message is not a JSON object");
            }
            return node;
        } catch (InvalidEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidEventException("Kafka message is not valid JSON: " + ex.getMessage(), ex);
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Reads ["A5","A6"] (or, leniently, "A5, A6") into a list; anything else gives an empty list. */
    private static List<String> textList(JsonNode json, String field) {
        JsonNode node = json.get(field);
        List<String> result = new ArrayList<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isNull() && !item.asText().isBlank()) {
                    result.add(item.asText().trim());
                }
            }
        } else if (node.isTextual()) {
            for (String part : node.asText().split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    private static Long longValue(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.valueOf(node.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
