package com.example.fraud.consumer.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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
        assertThat(meterRegistry.timer(FraudConsumerMetrics.REDIS_STATE_LATENCY).count())
                .isEqualTo(1);
    }

    @Test
    void recordsDetectionProcessingLatencyTimer() {
        metrics.recordDetectionProcessingLatency(Duration.ofMillis(25));

        assertThat(meterRegistry.timer(FraudConsumerMetrics.DETECTION_PROCESSING_LATENCY).count())
                .isEqualTo(1);
    }

    @Test
    void ignoresNegativeDetectionProcessingLatency() {
        metrics.recordDetectionProcessingLatency(Duration.ofMillis(-1));

        assertThat(meterRegistry.timer(FraudConsumerMetrics.DETECTION_PROCESSING_LATENCY).count())
                .isZero();
    }

    @Test
    void incrementsDltPublishedCounter() {
        metrics.incrementDltPublished();

        assertThat(meterRegistry.counter(FraudConsumerMetrics.DLT_PUBLISHED_TOTAL).count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsStageTimersWithoutHighCardinalityTags() {
        metrics.recordRuleProcessingLatency(() -> "rule-result");
        metrics.recordResultSinkLatency(() -> "sink-result");
        metrics.recordConsumerServiceLatency(Duration.ofMillis(10));

        assertThat(meterRegistry.timer(FraudConsumerMetrics.RULE_PROCESSING_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(FraudConsumerMetrics.RESULT_SINK_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.timer(FraudConsumerMetrics.CONSUMER_SERVICE_LATENCY).count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters())
                .filteredOn(meter -> meter.getId().getName().startsWith("fraud."))
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().matches("eventId|traceId|userId|accountId|offset")));
    }

    @Test
    void countsEveryConsumerDeliveryAttemptIncludingRedelivery() {
        metrics.incrementConsumerDelivery();
        metrics.incrementConsumerDelivery();

        assertThat(meterRegistry.counter(FraudConsumerMetrics.STREAM_CONSUMER_DELIVERY_TOTAL).count())
                .isEqualTo(2.0);
    }

    @Test
    void recordsCreateTimeToConsumerDelayAndRejectsInvalidDurations() {
        Instant consumerStartedAt = Instant.parse("2026-08-19T00:00:02Z");
        metrics.recordProducerToConsumerDelay(
                Instant.parse("2026-08-19T00:00:00Z").toEpochMilli(),
                consumerStartedAt
        );
        metrics.recordProducerToConsumerDelay(-1, consumerStartedAt);
        metrics.recordProducerToConsumerDelay(
                Instant.parse("2026-08-19T00:00:03Z").toEpochMilli(),
                consumerStartedAt
        );

        assertThat(meterRegistry.timer(FraudConsumerMetrics.PRODUCER_TO_CONSUMER_DELAY).count()).isEqualTo(1);
    }

    @Test
    void recordsIngressAgeAndRejectsFutureEventDuration() {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-19T00:00:05Z");
        metrics.recordIngressAge(OffsetDateTime.parse("2026-08-19T00:00:00Z"), receivedAt);
        metrics.recordIngressAge(OffsetDateTime.parse("2026-08-19T00:00:06Z"), receivedAt);
        metrics.recordIngressAge(null, receivedAt);

        assertThat(meterRegistry.timer(FraudConsumerMetrics.EVENT_INGRESS_AGE).count()).isEqualTo(1);
    }
}
