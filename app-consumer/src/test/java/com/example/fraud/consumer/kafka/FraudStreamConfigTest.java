package com.example.fraud.consumer.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FraudStreamConfigTest {

    @Test
    void acceptsLiveTopicGroupAndNamespaceInLiveMode() {
        FraudStreamConfig.validate(
                new FraudStreamProperties(FraudStreamMode.LIVE),
                "live",
                KafkaTopicNames.TRANSACTION_EVENTS,
                "fraud-event-consumer"
        );
    }

    @Test
    void acceptsReplayTopicGroupAndNamespaceInReplayMode() {
        FraudStreamConfig.validate(
                new FraudStreamProperties(FraudStreamMode.REPLAY),
                "replay",
                KafkaTopicNames.TRANSACTION_EVENTS_REPLAY,
                "fraud-event-replay-consumer"
        );
    }

    @Test
    void rejectsReplayTopicWithLiveNamespace() {
        assertThatThrownBy(() -> FraudStreamConfig.validate(
                new FraudStreamProperties(FraudStreamMode.REPLAY),
                "live",
                KafkaTopicNames.TRANSACTION_EVENTS_REPLAY,
                "fraud-event-replay-consumer"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis namespace replay");
    }

    @Test
    void rejectsReplayTopicWithLiveConsumerGroup() {
        assertThatThrownBy(() -> FraudStreamConfig.validate(
                new FraudStreamProperties(FraudStreamMode.REPLAY),
                "replay",
                KafkaTopicNames.TRANSACTION_EVENTS_REPLAY,
                "fraud-event-consumer"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumer group fraud-event-replay-consumer");
    }
}
