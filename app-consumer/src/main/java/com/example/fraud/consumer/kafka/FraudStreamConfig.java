package com.example.fraud.consumer.kafka;

import com.example.fraud.consumer.redis.SlidingWindowProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FraudStreamProperties.class)
public class FraudStreamConfig {

    private static final String LIVE_GROUP = "fraud-event-consumer";
    private static final String REPLAY_GROUP = "fraud-event-replay-consumer";
    private static final String LIVE_NAMESPACE = "live";
    private static final String REPLAY_NAMESPACE = "replay";

    @Bean
    InitializingBean fraudStreamModeValidator(
            FraudStreamProperties streamProperties,
            SlidingWindowProperties slidingWindowProperties,
            @Value("${fraud.consumer.topic:" + KafkaTopicNames.TRANSACTION_EVENTS + "}") String topic,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId
    ) {
        return () -> validate(streamProperties, slidingWindowProperties.namespace(), topic, consumerGroupId);
    }

    static void validate(
            FraudStreamProperties streamProperties,
            String namespace,
            String topic,
            String consumerGroupId
    ) {
        if (streamProperties.mode() == FraudStreamMode.LIVE) {
            validateLive(topic, consumerGroupId, namespace);
        } else {
            validateReplay(topic, consumerGroupId, namespace);
        }
    }

    private static void validateLive(String topic, String consumerGroupId, String namespace) {
        if (!KafkaTopicNames.TRANSACTION_EVENTS.equals(topic)) {
            throw new IllegalStateException("LIVE stream mode must consume " + KafkaTopicNames.TRANSACTION_EVENTS);
        }
        if (!LIVE_GROUP.equals(consumerGroupId)) {
            throw new IllegalStateException("LIVE stream mode must use consumer group " + LIVE_GROUP);
        }
        if (!LIVE_NAMESPACE.equals(namespace)) {
            throw new IllegalStateException("LIVE stream mode must use Redis namespace " + LIVE_NAMESPACE);
        }
    }

    private static void validateReplay(String topic, String consumerGroupId, String namespace) {
        if (!KafkaTopicNames.TRANSACTION_EVENTS_REPLAY.equals(topic)) {
            throw new IllegalStateException("REPLAY stream mode must consume " + KafkaTopicNames.TRANSACTION_EVENTS_REPLAY);
        }
        if (!REPLAY_GROUP.equals(consumerGroupId)) {
            throw new IllegalStateException("REPLAY stream mode must use consumer group " + REPLAY_GROUP);
        }
        if (!REPLAY_NAMESPACE.equals(namespace)) {
            throw new IllegalStateException("REPLAY stream mode must use Redis namespace " + REPLAY_NAMESPACE);
        }
    }
}
