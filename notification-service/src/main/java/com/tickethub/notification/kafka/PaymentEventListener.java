package com.tickethub.notification.kafka;

import com.tickethub.notification.service.EventNotificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Reads topic "payment-events" (published by payment-service). */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final EventNotificationHandler handler;

    public PaymentEventListener(EventNotificationHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(id = "payment-events-listener",
            topics = "${notification.kafka.payment-topic:payment-events}",
            groupId = "${notification.kafka.group-id:notification-service}",
            autoStartup = "${notification.kafka.enabled:true}")
    public void onMessage(String payload) {
        log.debug("payment-events message received");
        handler.handlePaymentEvent(payload);
    }
}
