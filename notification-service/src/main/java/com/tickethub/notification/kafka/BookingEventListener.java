package com.tickethub.notification.kafka;

import com.tickethub.notification.service.EventNotificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Reads topic "booking-events" (published by booking-service). */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final EventNotificationHandler handler;

    public BookingEventListener(EventNotificationHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(id = "booking-events-listener",
            topics = "${notification.kafka.booking-topic:booking-events}",
            groupId = "${notification.kafka.group-id:notification-service}",
            autoStartup = "${notification.kafka.enabled:true}")
    public void onMessage(String payload) {
        log.debug("booking-events message received");
        handler.handleBookingEvent(payload);
    }
}
