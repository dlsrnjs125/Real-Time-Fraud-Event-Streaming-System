package com.example.fraud.consumer.metrics;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
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

    private final MeterRegistry meterRegistry;
    private final Timer redisWindowRecordLatency;
    private final Timer detectionProcessingLatency;

    public FraudConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redisWindowRecordLatency = Timer.builder(REDIS_WINDOW_RECORD_LATENCY)
                .description("Redis sliding window record and read latency")
                .register(meterRegistry);
        this.detectionProcessingLatency = Timer.builder(DETECTION_PROCESSING_LATENCY)
                .description("Kafka listener processing latency from message handling start to fraud result persistence")
                .register(meterRegistry);
    }

    public <T> T recordRedisWindowLatency(Supplier<T> supplier) {
        return redisWindowRecordLatency.record(supplier);
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
        if (duration == null || duration.isNegative()) {
            return;
        }
        detectionProcessingLatency.record(duration);
    }

    public void incrementDltPublished() {
        meterRegistry.counter(DLT_PUBLISHED_TOTAL).increment();
    }
}
