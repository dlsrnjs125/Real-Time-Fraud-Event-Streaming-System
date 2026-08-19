package com.example.fraud.consumer.metrics;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class FraudConsumerMetrics {

    public static final String REDIS_WINDOW_RECORD_LATENCY = "fraud.redis.window.record.latency";
    public static final String REDIS_WINDOW_DEGRADED_TOTAL = "fraud.redis.window.degraded.total";
    public static final String RULE_SKIPPED_TOTAL = "fraud.rule.skipped.total";
    public static final String DETECTION_DEGRADED_TOTAL = "fraud.detection.degraded.total";
    public static final String DETECTION_PROCESSING_LATENCY = "fraud.detection.processing.latency";
    public static final String DLT_PUBLISHED_TOTAL = "fraud.dlt.published.total";
    public static final String STREAM_CONSUMER_DELIVERY_TOTAL = "fraud.stream.consumer.delivery.total";
    public static final String REDIS_STATE_LATENCY = "fraud.redis.state.latency";
    public static final String RULE_PROCESSING_LATENCY = "fraud.rule.processing.latency";
    public static final String RESULT_SINK_LATENCY = "fraud.result.sink.latency";
    public static final String CONSUMER_SERVICE_LATENCY = "fraud.consumer.service.latency";
    public static final String PRODUCER_TO_CONSUMER_DELAY = "fraud.kafka.producer.to.consumer.delay";
    public static final String EVENT_INGRESS_AGE = "fraud.event.ingress.age";

    private final MeterRegistry meterRegistry;
    private final Timer redisWindowRecordLatency;
    private final Timer detectionProcessingLatency;
    private final Timer redisStateLatency;
    private final Timer ruleProcessingLatency;
    private final Timer resultSinkLatency;
    private final Timer consumerServiceLatency;
    private final Timer producerToConsumerDelay;
    private final Timer eventIngressAge;

    public FraudConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redisWindowRecordLatency = Timer.builder(REDIS_WINDOW_RECORD_LATENCY)
                .description("Redis sliding window record and read latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.detectionProcessingLatency = Timer.builder(DETECTION_PROCESSING_LATENCY)
                .description("Kafka listener processing latency from message handling start to fraud result persistence")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.redisStateLatency = timer(REDIS_STATE_LATENCY, "Redis state update and read latency");
        this.ruleProcessingLatency = timer(RULE_PROCESSING_LATENCY, "Fraud rule engine processing latency");
        this.resultSinkLatency = timer(RESULT_SINK_LATENCY, "Fraud detection result sink latency");
        this.consumerServiceLatency = timer(CONSUMER_SERVICE_LATENCY, "Kafka listener delivery service latency");
        this.producerToConsumerDelay = timer(
                PRODUCER_TO_CONSUMER_DELAY,
                "Kafka producer CreateTime to Consumer processing start delay"
        );
        this.eventIngressAge = timer(EVENT_INGRESS_AGE, "Event occurrence to API receipt age");
    }

    public <T> T recordRedisWindowLatency(Supplier<T> supplier) {
        return redisStateLatency.record(() -> redisWindowRecordLatency.record(supplier));
    }

    public <T> T recordRuleProcessingLatency(Supplier<T> supplier) {
        return ruleProcessingLatency.record(supplier);
    }

    public <T> T recordResultSinkLatency(Supplier<T> supplier) {
        return resultSinkLatency.record(supplier);
    }

    public void incrementConsumerDelivery() {
        meterRegistry.counter(STREAM_CONSUMER_DELIVERY_TOTAL).increment();
    }

    public void recordConsumerServiceLatency(Duration duration) {
        recordNonNegative(consumerServiceLatency, duration);
    }

    public void recordProducerToConsumerDelay(long kafkaTimestamp, Instant consumerStartedAt) {
        if (kafkaTimestamp < 0 || consumerStartedAt == null) {
            return;
        }
        recordNonNegative(
                producerToConsumerDelay,
                Duration.between(Instant.ofEpochMilli(kafkaTimestamp), consumerStartedAt)
        );
    }

    public void recordIngressAge(OffsetDateTime eventTime, OffsetDateTime receivedAt) {
        if (eventTime == null || receivedAt == null) {
            return;
        }
        recordNonNegative(eventIngressAge, Duration.between(eventTime.toInstant(), receivedAt.toInstant()));
    }

    public void incrementRedisDegraded() {
        meterRegistry.counter(REDIS_WINDOW_DEGRADED_TOTAL).increment();
    }

    public void incrementSkippedRule(FraudRuleCode ruleCode) {
        meterRegistry.counter(RULE_SKIPPED_TOTAL, "rule", ruleCode.name()).increment();
    }

    public void incrementDetectionDegraded() {
        meterRegistry.counter(DETECTION_DEGRADED_TOTAL).increment();
    }

    public void recordDetectionProcessingLatency(Duration duration) {
        recordNonNegative(detectionProcessingLatency, duration);
    }

    public void incrementDltPublished() {
        meterRegistry.counter(DLT_PUBLISHED_TOTAL).increment();
    }

    private Timer timer(String name, String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private void recordNonNegative(Timer timer, Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        timer.record(duration);
    }
}
