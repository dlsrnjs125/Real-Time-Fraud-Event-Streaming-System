package com.example.fraud.consumer.metrics;

import com.example.fraud.common.event.FraudRuleCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class FraudConsumerMetrics {

    public static final String KAFKA_QUEUE_LATENCY = "fraud.kafka.queue.latency";
    public static final String CONSUMER_PROCESSING_LATENCY = "fraud.consumer.processing.latency";
    public static final String REDIS_WINDOW_LATENCY = "fraud.redis.window.latency";
    public static final String RULE_PROCESSING_LATENCY = "fraud.rule.processing.latency";
    public static final String DB_PERSISTENCE_LATENCY = "fraud.db.persistence.latency";
    public static final String EVENT_E2E_LATENCY = "fraud.event.e2e.latency";
    public static final String DB_OPERATION_PROCESSING_LOG = "processing_log";
    public static final String DB_OPERATION_FRAUD_RESULT_LOOKUP = "fraud_result_lookup";
    public static final String DB_OPERATION_FRAUD_RESULT = "fraud_result";
    public static final String REDIS_WINDOW_DEGRADED_TOTAL = "fraud.redis.window.degraded.total";
    public static final String RULE_SKIPPED_TOTAL = "fraud.rule.skipped.total";
    public static final String DETECTION_DEGRADED_TOTAL = "fraud.detection.degraded.total";
    public static final String DLT_PUBLISHED_TOTAL = "fraud.dlt.published.total";

    private final MeterRegistry meterRegistry;
    private final Timer kafkaQueueLatency;
    private final Timer consumerProcessingLatency;
    private final Timer redisWindowLatency;
    private final Timer ruleProcessingLatency;
    private final Timer eventE2eLatency;
    private final Map<String, Timer> dbPersistenceLatencies;

    public FraudConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.kafkaQueueLatency = latencyTimer(KAFKA_QUEUE_LATENCY, "Kafka record queue latency");
        this.consumerProcessingLatency = latencyTimer(
                CONSUMER_PROCESSING_LATENCY,
                "Consumer processing latency from listener entry to fraud result persistence"
        );
        this.redisWindowLatency = latencyTimer(
                REDIS_WINDOW_LATENCY,
                "Redis sliding window record and read latency"
        );
        this.ruleProcessingLatency = latencyTimer(
                RULE_PROCESSING_LATENCY,
                "Fraud rule engine processing latency"
        );
        this.eventE2eLatency = latencyTimer(
                EVENT_E2E_LATENCY,
                "Business event latency from event time to fraud result persistence"
        );
        this.dbPersistenceLatencies = Map.of(
                DB_OPERATION_PROCESSING_LOG, dbPersistenceTimer(DB_OPERATION_PROCESSING_LOG),
                DB_OPERATION_FRAUD_RESULT_LOOKUP, dbPersistenceTimer(DB_OPERATION_FRAUD_RESULT_LOOKUP),
                DB_OPERATION_FRAUD_RESULT, dbPersistenceTimer(DB_OPERATION_FRAUD_RESULT)
        );
    }

    private Timer latencyTimer(String name, String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void recordKafkaQueueLatency(Duration duration) {
        recordNonNegative(kafkaQueueLatency, duration);
    }

    public void recordConsumerProcessingLatency(Duration duration) {
        recordNonNegative(consumerProcessingLatency, duration);
    }

    public <T> T recordRedisWindowLatency(Supplier<T> supplier) {
        return redisWindowLatency.record(supplier);
    }

    public <T> T recordRuleProcessingLatency(Supplier<T> supplier) {
        return ruleProcessingLatency.record(supplier);
    }

    public <T> T recordDbPersistenceLatency(String operation, Supplier<T> supplier) {
        Timer timer = dbPersistenceLatencies.get(operation);
        if (timer == null) {
            throw new IllegalArgumentException("Unsupported DB metric operation: " + operation);
        }
        return timer.record(supplier);
    }

    private Timer dbPersistenceTimer(String operation) {
        return Timer.builder(DB_PERSISTENCE_LATENCY)
                .description("Synchronous PostgreSQL operation latency in the Consumer path")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void recordEventE2eLatency(Duration duration) {
        recordNonNegative(eventE2eLatency, duration);
    }

    private void recordNonNegative(Timer timer, Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        timer.record(duration);
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

    public void incrementDltPublished() {
        meterRegistry.counter(DLT_PUBLISHED_TOTAL).increment();
    }
}
