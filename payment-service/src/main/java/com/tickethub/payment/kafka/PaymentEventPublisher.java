package com.tickethub.payment.kafka;

import com.tickethub.payment.dto.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final String topic;
    private final boolean kafkaEnabled;

    public PaymentEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, PaymentEvent> kafkaTemplate,
            @Value("${tickethub.kafka.payment-topic:payment-events}") String topic,
            @Value("${tickethub.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.kafkaEnabled = kafkaEnabled;
    }

    public void publish(PaymentEvent event) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.info("Kafka is disabled or unconfigured locally. Skipping event publish for bookingId={}", event.bookingId());
            return;
        }

        try {
            String key = String.valueOf(event.bookingId());
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Could not publish {} for booking {} to Kafka: {}",
                            event.eventType(), event.bookingId(), ex.getMessage());
                } else {
                    log.info("Published {} for booking {} to topic {} (partition {}, offset {})",
                            event.eventType(), event.bookingId(), topic,
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (Throwable e) {
            // Guardrail: A Kafka metadata failure must NEVER compromise the saved payment record
            log.warn("Kafka broker unreachable. Skipping event delivery for bookingId={}: {}",
                    event.bookingId(), e.getMessage());
        }
    }
}