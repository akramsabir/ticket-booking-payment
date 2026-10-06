package com.tickethub.booking.service;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells the rest of the system "booking #5 is now CONFIRMED / CANCELLED / EXPIRED" (Kafka topic "booking-events").
 * notification-service reads it and emails the customer.
 *
 * Two rules that matter now that these events become customer-visible emails:
 *
 *  1. SEND ONLY AFTER THE DATABASE COMMIT. Callers publish from inside a transaction. If we sent immediately and the
 *     transaction then rolled back (for example two requests changed the booking at the same moment), the customer
 *     would get a "confirmed" email for a booking that is not confirmed. So when a transaction is running we wait for
 *     its commit and send then; if it rolls back, nothing is sent.
 *
 *  2. BUILD THE MESSAGE NOW, send it later. The seats are loaded lazily and can only be read while the transaction is
 *     still open, so the payload is assembled at publish() time and only the sending is postponed.
 *
 * Sending is best effort: a Kafka problem is logged but never makes a booking fail (the booking is already saved).
 *
 * Message format (a contract with notification-service - tell its owner before renaming a field):
 *   { bookingId, customerId, showId, status, totalAmount,
 *     customerEmail?, showTitle?, venue?, showTime?, seats? ["A5","A6"] }      ("?" = only present when known)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${booking.kafka.enabled:false}")
    private boolean enabled;

    @Value("${booking.kafka.topic:booking-events}")
    private String topic;

    public void publish(Booking booking) {
        if (!enabled) return;

        Map<String, Object> event = toEvent(booking);
        String key = String.valueOf(booking.getId());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(key, event);
                }
            });
        } else {
            send(key, event);   // no transaction around us (e.g. a unit test): nothing to wait for
        }
    }

    // package-private so a test can check exactly what would be sent
    Map<String, Object> toEvent(Booking booking) {
        // LinkedHashMap, not Map.of: Map.of throws on null values, and a missing detail must never break publishing
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("bookingId", booking.getId());
        event.put("customerId", booking.getCustomerId());
        event.put("showId", booking.getShowId());
        event.put("status", booking.getStatus().name());
        event.put("totalAmount", booking.getTotalAmount() == null ? "0" : booking.getTotalAmount().toPlainString());
        putIfPresent(event, "customerEmail", booking.getCustomerEmail());
        putIfPresent(event, "showTitle", booking.getShowTitle());
        putIfPresent(event, "venue", booking.getVenueName());
        putIfPresent(event, "showTime", booking.getShowTime());

        List<String> seats = new ArrayList<>();
        for (BookingSeat seat : booking.getSeats()) {
            // the readable label ("A5"); older bookings have none, so fall back to the internal seat id
            String label = seat.getSeatLabel() != null && !seat.getSeatLabel().isBlank() ? seat.getSeatLabel() : seat.getSeatId();
            if (label != null) {
                seats.add(label);
            }
        }
        if (!seats.isEmpty()) {
            event.put("seats", seats);
        }
        return event;
    }

    private void send(String key, Map<String, Object> event) {
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (Exception ex) {
            // Kafka must not make a completed booking fail when event publishing is best-effort.
            log.error("Unable to publish booking event for bookingId={}", key, ex);
        }
    }

    private static void putIfPresent(Map<String, Object> event, String field, String value) {
        if (value != null && !value.isBlank()) {
            event.put(field, value);
        }
    }
}
