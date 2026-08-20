package com.example.fraud.api.transaction.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionIntakeMetrics {

    public static final String RECEIPT_PERSISTED_TOTAL = "fraud.stream.intake.receipt.persisted.total";
    public static final String KAFKA_PUBLISH_SUCCESS_TOTAL = "fraud.stream.kafka.publish.success.total";
    public static final String KAFKA_PUBLISH_FAILURE_TOTAL = "fraud.stream.kafka.publish.failure.total";

    private final Counter receiptPersisted;
    private final Counter kafkaPublishSuccess;
    private final Counter kafkaPublishFailure;

    public TransactionIntakeMetrics(MeterRegistry meterRegistry) {
        this.receiptPersisted = meterRegistry.counter(RECEIPT_PERSISTED_TOTAL);
        this.kafkaPublishSuccess = meterRegistry.counter(KAFKA_PUBLISH_SUCCESS_TOTAL);
        this.kafkaPublishFailure = meterRegistry.counter(KAFKA_PUBLISH_FAILURE_TOTAL);
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

    public enum PublishOutcome {
        SUCCESS,
        FAILURE
    }
}
