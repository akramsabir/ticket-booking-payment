package com.tickethub.notification.config;

import com.tickethub.notification.exception.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * What happens when processing a Kafka message throws:
 *   - broken message (InvalidEventException)  -> straight to "<topic>.DLT", no retry
 *   - anything else (e.g. DB down)            -> retried 3 more times, 2 seconds apart, then "<topic>.DLT"
 * An unreachable SMTP server is NOT an error here: the notification is saved as FAILED and the retry job resends it.
 */
@Configuration
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    @Bean
    public NewTopic paymentEventsTopic(NotificationProperties props) {
        return TopicBuilder.name(props.getKafka().getPaymentTopic()).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bookingEventsTopic(NotificationProperties props) {
        return TopicBuilder.name(props.getKafka().getBookingTopic()).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsDeadLetterTopic(NotificationProperties props) {
        return TopicBuilder.name(props.getKafka().getPaymentTopic() + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bookingEventsDeadLetterTopic(NotificationProperties props) {
        return TopicBuilder.name(props.getKafka().getBookingTopic() + ".DLT").partitions(1).replicas(1).build();
    }
}
