package com.example.fraud.consumer.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FraudConsumerMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final FraudConsumerMetrics metrics = new FraudConsumerMetrics(meterRegistry);

    @Test
    void incrementsRedisDegradedCounter() {
        metrics.incrementRedisDegraded();

        assertThat(meterRegistry.counter(FraudConsumerMetrics.REDIS_WINDOW_DEGRADED_TOTAL).count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsDetectionDegradedCounter() {
        metrics.incrementDetectionDegraded();

        assertThat(meterRegistry.counter(FraudConsumerMetrics.DETECTION_DEGRADED_TOTAL).count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsSkippedRuleCounterWithLowCardinalityRuleTag() {
        metrics.incrementSkippedRule(FraudRuleCode.RAPID_TRANSACTION_COUNT);

        assertThat(meterRegistry.counter(
                FraudConsumerMetrics.RULE_SKIPPED_TOTAL,
                "rule",
                FraudRuleCode.RAPID_TRANSACTION_COUNT.name()
        ).count()).isEqualTo(1.0);
    }

    @Test
    void recordsRedisWindowLatencyTimer() {
        String result = metrics.recordRedisWindowLatency(() -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(meterRegistry.timer(FraudConsumerMetrics.REDIS_WINDOW_LATENCY).count())
                .isEqualTo(1);
    }

    @Test
    void recordsPhaseZeroLatencyTimers() {
        metrics.recordKafkaQueueLatency(Duration.ofMillis(10));
        metrics.recordConsumerProcessingLatency(Duration.ofMillis(25));
        metrics.recordRuleProcessingLatency(() -> "ok");
        metrics.recordDbPersistenceLatency(FraudConsumerMetrics.DB_OPERATION_FRAUD_RESULT, () -> "ok");
        metrics.recordEventE2eLatency(Duration.ofMillis(50));

        assertThat(meterRegistry.timer(FraudConsumerMetrics.KAFKA_QUEUE_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(FraudConsumerMetrics.CONSUMER_PROCESSING_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(FraudConsumerMetrics.RULE_PROCESSING_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(
                FraudConsumerMetrics.DB_PERSISTENCE_LATENCY,
                "operation",
                "fraud_result"
        ).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(FraudConsumerMetrics.EVENT_E2E_LATENCY).count()).isEqualTo(1);
    }

    @Test
    void ignoresNegativeClockBasedLatencies() {
        metrics.recordKafkaQueueLatency(Duration.ofMillis(-1));
        metrics.recordConsumerProcessingLatency(Duration.ofMillis(-1));
        metrics.recordEventE2eLatency(Duration.ofMillis(-1));

        assertThat(meterRegistry.timer(FraudConsumerMetrics.KAFKA_QUEUE_LATENCY).count()).isZero();
        assertThat(meterRegistry.timer(FraudConsumerMetrics.CONSUMER_PROCESSING_LATENCY).count()).isZero();
        assertThat(meterRegistry.timer(FraudConsumerMetrics.EVENT_E2E_LATENCY).count()).isZero();
    }

    @Test
    void rejectsUnboundedDbOperationTags() {
        assertThatThrownBy(() -> metrics.recordDbPersistenceLatency("event-specific-operation", () -> "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported DB metric operation");
    }

    @Test
    void incrementsDltPublishedCounter() {
        metrics.incrementDltPublished();

        assertThat(meterRegistry.counter(FraudConsumerMetrics.DLT_PUBLISHED_TOTAL).count())
                .isEqualTo(1.0);
    }
}
