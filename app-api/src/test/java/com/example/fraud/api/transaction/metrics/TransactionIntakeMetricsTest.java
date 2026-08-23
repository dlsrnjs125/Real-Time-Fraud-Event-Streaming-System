package com.example.fraud.api.transaction.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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

    @Test
    void recordsPhase1IntakeStageTimersWithoutHighCardinalityTags() {
        metrics.recordApiIntakeServiceLatency(Duration.ofMillis(10));
        metrics.recordApiIntakeTransactionLatencyAfterCompletion(System.nanoTime());
        metrics.recordReceiptPersistenceLatency(Duration.ofMillis(3));
        metrics.recordKafkaPublishWaitLatency(Duration.ofMillis(4));
        metrics.recordReceiptStatusUpdateLatency(Duration.ofMillis(2));

        assertThat(registry.timer(TransactionIntakeMetrics.API_INTAKE_SERVICE_LATENCY).count()).isEqualTo(1);
        assertThat(registry.timer(TransactionIntakeMetrics.API_INTAKE_TRANSACTION_LATENCY).count()).isEqualTo(1);
        assertThat(registry.timer(TransactionIntakeMetrics.RECEIPT_PERSISTENCE_LATENCY).count()).isEqualTo(1);
        assertThat(registry.timer(TransactionIntakeMetrics.KAFKA_PUBLISH_WAIT_LATENCY).count()).isEqualTo(1);
        assertThat(registry.timer(TransactionIntakeMetrics.RECEIPT_STATUS_UPDATE_LATENCY).count()).isEqualTo(1);
        assertThat(registry.getMeters())
                .filteredOn(meter -> meter.getId().getName().startsWith("fraud."))
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().matches("eventId|traceId|userId|accountId|deviceId")));
    }

    @Test
    void recordsTransactionLatencyAfterCompletionWhenSynchronizationIsActive() {
        beginTransactionSynchronization();

        metrics.recordApiIntakeTransactionLatencyAfterCompletion(System.nanoTime());

        assertThat(registry.timer(TransactionIntakeMetrics.API_INTAKE_TRANSACTION_LATENCY).count()).isZero();
        commitSynchronization();
        assertThat(registry.timer(TransactionIntakeMetrics.API_INTAKE_TRANSACTION_LATENCY).count()).isEqualTo(1);
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
