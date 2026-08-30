package com.example.fraud.api.kafka;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FraudStreamProperties.class)
public class FraudStreamConfig {

    @Bean
    ApplicationRunner fraudStreamModeValidator(FraudStreamProperties properties) {
        return args -> validate(properties);
    }

    static void validate(FraudStreamProperties properties) {
        String topic = properties.resolvedProducerTopic();
        if (properties.mode() == FraudStreamMode.LIVE && !KafkaTopicNames.TRANSACTION_EVENTS.equals(topic)) {
            throw new IllegalStateException("LIVE stream mode must publish to " + KafkaTopicNames.TRANSACTION_EVENTS);
        }
        if (properties.mode() == FraudStreamMode.REPLAY && !KafkaTopicNames.TRANSACTION_EVENTS_REPLAY.equals(topic)) {
            throw new IllegalStateException(
                    "REPLAY stream mode must publish to " + KafkaTopicNames.TRANSACTION_EVENTS_REPLAY
            );
        }
    }
}
