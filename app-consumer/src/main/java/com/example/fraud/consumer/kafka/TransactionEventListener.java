package com.example.fraud.consumer.kafka;

import com.example.fraud.common.event.TransactionEventMessage;
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
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final EventProcessingLogService processingLogService;
    private final RecentTransactionWindowStore recentTransactionWindowStore;
    private final FraudRuleEngine fraudRuleEngine;
    private final FraudDetectionResultService fraudDetectionResultService;
    private final DeadLetterEventService deadLetterEventService;
    private final FraudConsumerMetrics metrics;
    private final Clock clock;
    private final String consumerGroupId;

    public TransactionEventListener(
            EventProcessingLogService processingLogService,
            RecentTransactionWindowStore recentTransactionWindowStore,
            FraudRuleEngine fraudRuleEngine,
            FraudDetectionResultService fraudDetectionResultService,
            DeadLetterEventService deadLetterEventService,
            FraudConsumerMetrics metrics,
            Clock clock,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId
    ) {
        this.processingLogService = processingLogService;
        this.recentTransactionWindowStore = recentTransactionWindowStore;
        this.fraudRuleEngine = fraudRuleEngine;
        this.fraudDetectionResultService = fraudDetectionResultService;
        this.deadLetterEventService = deadLetterEventService;
        this.metrics = metrics;
        this.clock = clock;
        this.consumerGroupId = consumerGroupId;
    }

    @KafkaListener(topics = KafkaTopicNames.TRANSACTION_EVENTS)
    public void onMessage(ConsumerRecord<String, TransactionEventMessage> record, Acknowledgment acknowledgment) {
        Instant processingStartedAt = clock.instant();
        TransactionEventMessage message = record.value();
        metrics.incrementConsumerDelivery();
        metrics.recordProducerToConsumerDelay(record.timestamp(), processingStartedAt);
        metrics.recordIngressAge(message.eventTime(), message.receivedAt());

        try {
            processMessage(record, acknowledgment, processingStartedAt, message);
        } finally {
            metrics.recordConsumerServiceLatency(Duration.between(processingStartedAt, clock.instant()));
        }
    }

    private void processMessage(
            ConsumerRecord<String, TransactionEventMessage> record,
            Acknowledgment acknowledgment,
            Instant processingStartedAt,
            TransactionEventMessage message
    ) {

        ProcessingLogResult result = processingLogService.recordProcessedEvent(
                message,
                record.topic(),
                record.partition(),
                record.offset(),
                consumerGroupId
        );
        if (fraudDetectionResultService.existsResultForEventId(message.eventId())) {
            acknowledgment.acknowledge();
            log.info(
                    "transaction event duplicate fraud result skipped traceId={} eventId={} userId={} topic={} partition={} offset={} processingDuplicateSkipped={}",
                    message.traceId(),
                    message.eventId(),
                    message.userId(),
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result.duplicateSkipped()
            );
            return;
        }

        RecentTransactionWindowResult windowResult = recentTransactionWindowStore.recordAndGetWindow(message);
        FraudRuleEngineResult ruleResult;
        try {
            ruleResult = metrics.recordRuleProcessingLatency(() -> fraudRuleEngine.evaluate(message, windowResult));
        } catch (RuntimeException exception) {
            recordDeadLetterAndAcknowledge(record, acknowledgment, FailureStage.RULE_ENGINE_ERROR, exception);
            return;
        }
        FraudDetectionResultSaveResult saveResult = metrics.recordResultSinkLatency(
                () -> fraudDetectionResultService.saveResult(message, ruleResult)
        );
        recordDetectionMetrics(ruleResult, saveResult);
        if (!saveResult.duplicateSkipped()) {
            metrics.recordDetectionProcessingLatency(Duration.between(processingStartedAt, clock.instant()));
        }

        acknowledgment.acknowledge();

        log.info(
                "transaction event consumed traceId={} eventId={} userId={} topic={} partition={} offset={} processingDuplicateSkipped={} fraudDuplicateSkipped={} redisDegraded={} degradedReason={} transactionCount={} amountSum={} matchedRules={} skippedRules={} riskScore={} riskLevel={} decision={}",
                message.traceId(),
                message.eventId(),
                message.userId(),
                record.topic(),
                record.partition(),
                record.offset(),
                result.duplicateSkipped(),
                saveResult.duplicateSkipped(),
                windowResult.degraded(),
                windowResult.reason(),
                windowResult.transactionCount(),
                windowResult.amountSum(),
                ruleResult.matchedRules(),
                ruleResult.skippedRules(),
                ruleResult.riskScore(),
                ruleResult.riskLevel(),
                ruleResult.decision()
        );
    }

    private void recordDeadLetterAndAcknowledge(
            ConsumerRecord<String, TransactionEventMessage> record,
            Acknowledgment acknowledgment,
            FailureStage failureStage,
            RuntimeException exception
    ) {
        DeadLetterEventEntity event = deadLetterEventService.recordFailure(record, failureStage, exception);
        deadLetterEventService.publish(event, record.value(), OffsetDateTime.now(clock));
        acknowledgment.acknowledge();
        log.warn(
                "transaction event moved to dlt traceId={} eventId={} userId={} topic={} partition={} offset={} failureStage={} errorType={}",
                record.value().traceId(),
                record.value().eventId(),
                record.value().userId(),
                record.topic(),
                record.partition(),
                record.offset(),
                failureStage,
                exception.getClass().getSimpleName()
        );
    }

    private void recordDetectionMetrics(
            FraudRuleEngineResult ruleResult,
            FraudDetectionResultSaveResult saveResult
    ) {
        if (saveResult.duplicateSkipped()) {
            return;
        }
        if (ruleResult.degraded()) {
            metrics.incrementDetectionDegraded();
        }
        ruleResult.skippedRules().forEach(metrics::incrementSkippedRule);
    }
}
