package com.example.fraud.api.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud.stream")
public record FraudStreamProperties(
        FraudStreamMode mode,
        String producerTopic
) {

    public FraudStreamProperties {
        if (mode == null) {
            mode = FraudStreamMode.LIVE;
        }
    }

    public String resolvedProducerTopic() {
        if (producerTopic != null && !producerTopic.isBlank()) {
            return producerTopic;
        }
        return switch (mode) {
            case LIVE -> KafkaTopicNames.TRANSACTION_EVENTS;
            case REPLAY -> KafkaTopicNames.TRANSACTION_EVENTS_REPLAY;
        };
    }
}
