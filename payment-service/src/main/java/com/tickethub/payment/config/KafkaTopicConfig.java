package com.tickethub.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${tickethub.kafka.payment-topic:payment-events}")
    private String paymentTopic;

    @Bean
    public NewTopic paymentEventsTopic() {
        // Auto-creates the topic on startup so metadata requests never time out
        return TopicBuilder.name(paymentTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}