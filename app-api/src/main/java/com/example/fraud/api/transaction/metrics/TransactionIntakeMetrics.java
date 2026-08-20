package com.example.fraud.api.transaction.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionIntakeMetrics {

    public static final String RECEIPT_PERSISTED_TOTAL = "fraud.stream.intake.receipt.persisted.total";
    public static final String KAFKA_PUBLISH_SUCCESS_TOTAL = "fraud.stream.kafka.publish.success.total";
    public static final String KAFKA_PUBLISH_FAILURE_TOTAL = "fraud.stream.kafka.publish.failure.total";
    public static final String API_INTAKE_SERVICE_LATENCY = "fraud.api.intake.service.latency";
    public static final String RECEIPT_PERSISTENCE_LATENCY = "fraud.api.receipt.persistence.latency";
    public static final String KAFKA_PUBLISH_WAIT_LATENCY = "fraud.kafka.publish.wait.latency";
    public static final String RECEIPT_STATUS_UPDATE_LATENCY = "fraud.api.receipt.status.update.latency";

    private final Counter receiptPersisted;
    private final Counter kafkaPublishSuccess;
    private final Counter kafkaPublishFailure;
    private final Timer apiIntakeServiceLatency;
    private final Timer receiptPersistenceLatency;
    private final Timer kafkaPublishWaitLatency;
    private final Timer receiptStatusUpdateLatency;

    public TransactionIntakeMetrics(MeterRegistry meterRegistry) {
        this.receiptPersisted = meterRegistry.counter(RECEIPT_PERSISTED_TOTAL);
        this.kafkaPublishSuccess = meterRegistry.counter(KAFKA_PUBLISH_SUCCESS_TOTAL);
        this.kafkaPublishFailure = meterRegistry.counter(KAFKA_PUBLISH_FAILURE_TOTAL);
        this.apiIntakeServiceLatency = timer(
                meterRegistry,
                API_INTAKE_SERVICE_LATENCY,
                "Transaction intake service latency"
        );
        this.receiptPersistenceLatency = timer(
                meterRegistry,
                RECEIPT_PERSISTENCE_LATENCY,
                "Transaction receipt initial persistence latency"
        );
        this.kafkaPublishWaitLatency = timer(
                meterRegistry,
                KAFKA_PUBLISH_WAIT_LATENCY,
                "Synchronous Kafka publish wait latency inside intake transaction"
        );
        this.receiptStatusUpdateLatency = timer(
                meterRegistry,
                RECEIPT_STATUS_UPDATE_LATENCY,
                "Transaction receipt publish status update latency"
        );
    }

    public void recordAfterCommit(PublishOutcome outcome) {
        Runnable recorder = () -> recordCommittedOutcome(outcome);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recorder.run();
                }
            });
            return;
        }
        recorder.run();
    }

    private void recordCommittedOutcome(PublishOutcome outcome) {
        receiptPersisted.increment();
        if (outcome == PublishOutcome.SUCCESS) {
            kafkaPublishSuccess.increment();
        } else {
            kafkaPublishFailure.increment();
        }
    }

    public void recordApiIntakeServiceLatency(Duration duration) {
        recordNonNegative(apiIntakeServiceLatency, duration);
    }

    public void recordReceiptPersistenceLatency(Duration duration) {
        recordNonNegative(receiptPersistenceLatency, duration);
    }

    public void recordKafkaPublishWaitLatency(Duration duration) {
        recordNonNegative(kafkaPublishWaitLatency, duration);
    }

    public void recordReceiptStatusUpdateLatency(Duration duration) {
        recordNonNegative(receiptStatusUpdateLatency, duration);
    }

    private Timer timer(MeterRegistry meterRegistry, String name, String description) {
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

    public enum PublishOutcome {
        SUCCESS,
        FAILURE
    }
}
