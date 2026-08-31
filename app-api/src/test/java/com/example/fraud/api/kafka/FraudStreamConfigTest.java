package com.example.fraud.api.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FraudStreamConfigTest {

    @Test
    void acceptsDefaultLiveProducerTopic() {
        FraudStreamConfig.validate(new FraudStreamProperties(FraudStreamMode.LIVE, null));
    }

    @Test
    void acceptsReplayProducerTopicInReplayMode() {
        FraudStreamConfig.validate(new FraudStreamProperties(FraudStreamMode.REPLAY, null));
    }

    @Test
    void rejectsReplayTopicInLiveMode() {
        assertThatThrownBy(() -> FraudStreamConfig.validate(new FraudStreamProperties(
                FraudStreamMode.LIVE,
                KafkaTopicNames.TRANSACTION_EVENTS_REPLAY
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE stream mode");
    }

    @Test
    void rejectsLiveTopicInReplayMode() {
        assertThatThrownBy(() -> FraudStreamConfig.validate(new FraudStreamProperties(
                FraudStreamMode.REPLAY,
                KafkaTopicNames.TRANSACTION_EVENTS
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPLAY stream mode");
    }
}
