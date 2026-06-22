package com.example.fraud.consumer.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.example.fraud.consumer.dlt.DeadLetterEventEntity;
import com.example.fraud.consumer.dlt.DeadLetterEventService;
import com.example.fraud.consumer.dlt.FailureStage;
import com.example.fraud.consumer.fraud.FraudDetectionResultSaveResult;
import com.example.fraud.consumer.fraud.FraudDetectionResultService;
import com.example.fraud.consumer.metrics.FraudConsumerMetrics;
import com.example.fraud.consumer.processing.EventProcessingLogService;
import com.example.fraud.consumer.processing.ProcessingLogResult;
import com.example.fraud.consumer.redis.RecentTransactionWindowResult;
import com.example.fraud.consumer.redis.RecentTransactionWindowStore;
import com.example.fraud.consumer.rule.FraudRuleEngine;
import com.example.fraud.consumer.rule.FraudRuleEngineResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class TransactionEventListenerTest {

    private final EventProcessingLogService processingLogService = mock(EventProcessingLogService.class);
    private final RecentTransactionWindowStore recentTransactionWindowStore = mock(RecentTransactionWindowStore.class);
    private final FraudRuleEngine fraudRuleEngine = mock(FraudRuleEngine.class);
    private final FraudDetectionResultService fraudDetectionResultService = mock(FraudDetectionResultService.class);
    private final DeadLetterEventService deadLetterEventService = mock(DeadLetterEventService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final FraudConsumerMetrics metrics = new FraudConsumerMetrics(meterRegistry);
    private final TransactionEventListener listener = new TransactionEventListener(
            processingLogService,
            recentTransactionWindowStore,
            fraudRuleEngine,
            fraudDetectionResultService,
            deadLetterEventService,
            metrics,
            "fraud-event-consumer"
    );

    @Test
    void acknowledgesAfterProcessingLogIsSaved() {
        TransactionEventMessage message = message("evt-listener-001");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                15L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                15L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        RecentTransactionWindowResult windowResult = normalWindowResult();
        FraudRuleEngineResult ruleResult = lowRiskResult();
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenReturn(ruleResult);
        when(fraudDetectionResultService.saveResult(message, ruleResult))
                .thenReturn(FraudDetectionResultSaveResult.saved());

        listener.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        assertThatMetric(FraudConsumerMetrics.DETECTION_DEGRADED_TOTAL).isZero();
    }

    @Test
    void acknowledgesWhenDuplicateOffsetIsAlreadyProcessed() {
        TransactionEventMessage message = message("evt-listener-duplicate");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                15L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                15L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.duplicate());
        RecentTransactionWindowResult windowResult = normalWindowResult();
        FraudRuleEngineResult ruleResult = lowRiskResult();
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenReturn(ruleResult);
        when(fraudDetectionResultService.saveResult(message, ruleResult))
                .thenReturn(FraudDetectionResultSaveResult.saved());

        listener.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void acknowledgesWithoutUpdatingRedisWhenFraudResultAlreadyExists() {
        TransactionEventMessage message = message("evt-listener-result-duplicate");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                16L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                2,
                16L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(true);

        listener.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(recentTransactionWindowStore, never()).recordAndGetWindow(message);
        verify(fraudRuleEngine, never()).evaluate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(fraudDetectionResultService, never()).saveResult(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotAcknowledgeWhenFraudResultSaveFails() {
        TransactionEventMessage message = message("evt-listener-result-fail");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                8L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                8L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        RecentTransactionWindowResult windowResult = normalWindowResult();
        FraudRuleEngineResult ruleResult = lowRiskResult();
        RuntimeException failure = new RuntimeException("fraud result database unavailable");
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenReturn(ruleResult);
        when(fraudDetectionResultService.saveResult(message, ruleResult)).thenThrow(failure);

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isSameAs(failure);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void acknowledgesAndMovesToDltWhenRuleEngineFails() {
        TransactionEventMessage message = message("evt-listener-rule-fail");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                9L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RuntimeException failure = new RuntimeException("rule engine failure");
        RecentTransactionWindowResult windowResult = normalWindowResult();
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                9L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenThrow(failure);
        DeadLetterEventEntity dltEvent = mock(DeadLetterEventEntity.class);
        when(deadLetterEventService.recordFailure(record, FailureStage.RULE_ENGINE_ERROR, failure))
                .thenReturn(dltEvent);

        listener.onMessage(record, acknowledgment);

        verify(deadLetterEventService).recordFailure(record, FailureStage.RULE_ENGINE_ERROR, failure);
        verify(deadLetterEventService).publish(
                org.mockito.ArgumentMatchers.eq(dltEvent),
                org.mockito.ArgumentMatchers.eq(message),
                org.mockito.ArgumentMatchers.any()
        );
        verify(acknowledgment).acknowledge();
        verify(fraudDetectionResultService, never()).saveResult(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotAcknowledgeWhenDltRecordFails() {
        TransactionEventMessage message = message("evt-listener-dlt-save-fail");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                19L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RuntimeException failure = new RuntimeException("rule engine failure");
        RuntimeException dltFailure = new RuntimeException("dlt database unavailable");
        RecentTransactionWindowResult windowResult = normalWindowResult();
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                19L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenThrow(failure);
        when(deadLetterEventService.recordFailure(record, FailureStage.RULE_ENGINE_ERROR, failure))
                .thenThrow(dltFailure);

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isSameAs(dltFailure);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void acknowledgesWhenRedisWindowIsDegradedAndFraudResultIsSaved() {
        TransactionEventMessage message = message("evt-listener-redis-degraded");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                1,
                21L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                1,
                21L,
                "fraud-event-consumer"
        )).thenReturn(ProcessingLogResult.processed());
        RecentTransactionWindowResult windowResult = RecentTransactionWindowResult.degraded("Redis unavailable");
        FraudRuleEngineResult ruleResult = new FraudRuleEngineResult(
                0,
                RiskLevel.LOW,
                FraudDecision.APPROVE,
                List.of(),
                List.of(
                        com.example.fraud.common.event.FraudRuleCode.RAPID_TRANSACTION_COUNT,
                        com.example.fraud.common.event.FraudRuleCode.WINDOW_AMOUNT_SUM
                ),
                true,
                "No fraud rule matched; Redis degraded mode: Redis unavailable"
        );
        when(fraudDetectionResultService.existsResultForEventId(message.eventId())).thenReturn(false);
        when(recentTransactionWindowStore.recordAndGetWindow(message)).thenReturn(windowResult);
        when(fraudRuleEngine.evaluate(message, windowResult)).thenReturn(ruleResult);
        when(fraudDetectionResultService.saveResult(message, ruleResult))
                .thenReturn(FraudDetectionResultSaveResult.saved());

        listener.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(fraudDetectionResultService).saveResult(message, ruleResult);
        assertThatMetric(FraudConsumerMetrics.DETECTION_DEGRADED_TOTAL).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                FraudConsumerMetrics.RULE_SKIPPED_TOTAL,
                "rule",
                com.example.fraud.common.event.FraudRuleCode.RAPID_TRANSACTION_COUNT.name()
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                FraudConsumerMetrics.RULE_SKIPPED_TOTAL,
                "rule",
                com.example.fraud.common.event.FraudRuleCode.WINDOW_AMOUNT_SUM.name()
        ).count()).isEqualTo(1.0);
    }

    @Test
    void doesNotAcknowledgeWhenProcessingLogSaveFails() {
        TransactionEventMessage message = message("evt-listener-fail");
        ConsumerRecord<String, TransactionEventMessage> record = new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                7L,
                "user-1001",
                message
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        RuntimeException failure = new RuntimeException("database unavailable");
        when(processingLogService.recordProcessedEvent(
                message,
                KafkaTopicNames.TRANSACTION_EVENTS,
                0,
                7L,
                "fraud-event-consumer"
        )).thenThrow(failure);

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isSameAs(failure);

        verify(acknowledgment, never()).acknowledge();
        verify(fraudRuleEngine, never()).evaluate(message);
        verify(recentTransactionWindowStore, never()).recordAndGetWindow(message);
        verify(fraudDetectionResultService, never()).saveResult(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private FraudRuleEngineResult lowRiskResult() {
        return new FraudRuleEngineResult(
                0,
                RiskLevel.LOW,
                FraudDecision.APPROVE,
                List.of(),
                List.of(),
                false,
                "No fraud rule matched"
        );
    }

    private RecentTransactionWindowResult normalWindowResult() {
        return RecentTransactionWindowResult.normal(1, BigDecimal.valueOf(120000));
    }

    private TransactionEventMessage message(String eventId) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-17T10:00:00Z");
        return new TransactionEventMessage(
                "v1",
                eventId,
                "user-1001",
                "acc-1001",
                TransactionEventType.PAYMENT,
                BigDecimal.valueOf(120000),
                "KRW",
                "merchant-001",
                "device-001",
                "SEOUL",
                eventTime,
                eventTime.plusSeconds(1),
                "trace-" + eventId
        );
    }

    private org.assertj.core.api.AbstractDoubleAssert<?> assertThatMetric(String metricName) {
        return org.assertj.core.api.Assertions.assertThat(meterRegistry.counter(metricName).count());
    }
}
