package com.example.fraud.consumer.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        assertThat(meterRegistry.timer(FraudConsumerMetrics.REDIS_WINDOW_RECORD_LATENCY).count())
                .isEqualTo(1);
    }
}
