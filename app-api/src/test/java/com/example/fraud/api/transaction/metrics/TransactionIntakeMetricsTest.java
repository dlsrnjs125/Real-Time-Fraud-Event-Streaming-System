package com.example.fraud.api.transaction.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionIntakeMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TransactionIntakeMetrics metrics = new TransactionIntakeMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void recordsReceiptAndPublishSuccessOnlyAfterCommit() {
        beginTransactionSynchronization();

        metrics.recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.SUCCESS);

        assertThat(count(TransactionIntakeMetrics.RECEIPT_PERSISTED_TOTAL)).isZero();
        commitSynchronization();
        assertThat(count(TransactionIntakeMetrics.RECEIPT_PERSISTED_TOTAL)).isEqualTo(1.0);
        assertThat(count(TransactionIntakeMetrics.KAFKA_PUBLISH_SUCCESS_TOTAL)).isEqualTo(1.0);
        assertThat(count(TransactionIntakeMetrics.KAFKA_PUBLISH_FAILURE_TOTAL)).isZero();
    }

    @Test
    void recordsReceiptAndPublishFailureOnlyAfterCommit() {
        beginTransactionSynchronization();

        metrics.recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.FAILURE);
        commitSynchronization();

        assertThat(count(TransactionIntakeMetrics.RECEIPT_PERSISTED_TOTAL)).isEqualTo(1.0);
        assertThat(count(TransactionIntakeMetrics.KAFKA_PUBLISH_SUCCESS_TOTAL)).isZero();
        assertThat(count(TransactionIntakeMetrics.KAFKA_PUBLISH_FAILURE_TOTAL)).isEqualTo(1.0);
    }

    @Test
    void doesNotRecordRolledBackReceipt() {
        beginTransactionSynchronization();

        metrics.recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.SUCCESS);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(count(TransactionIntakeMetrics.RECEIPT_PERSISTED_TOTAL)).isZero();
        assertThat(count(TransactionIntakeMetrics.KAFKA_PUBLISH_SUCCESS_TOTAL)).isZero();
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commitSynchronization() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private double count(String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
