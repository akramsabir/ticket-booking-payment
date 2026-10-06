package com.tickethub.notification;

import com.tickethub.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TicketHub Notification Service.
 *
 * Listens to Kafka topics (payment-events, booking-events) and also exposes a small REST API,
 * and sends EMAIL to the customer. Booking/Payment never wait for this service.
 */
@EnableKafka
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}