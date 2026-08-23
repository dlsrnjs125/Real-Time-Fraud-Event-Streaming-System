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
import com.example.fraud.consumer.redelivery.StatefulRedeliveryFailureInjector;
import com.example.fraud.consumer.redelivery.StatefulRedeliveryFailurePoint;
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
    private final StatefulRedeliveryFailureInjector redeliveryFailureInjector;
    private final FraudConsumerMetrics metrics;
    private final Clock clock;
    private final String consumerGroupId;
    private final Duration slowEventThreshold;

    public TransactionEventListener(
            EventProcessingLogService processingLogService,
            RecentTransactionWindowStore recentTransactionWindowStore,
            FraudRuleEngine fraudRuleEngine,
            FraudDetectionResultService fraudDetectionResultService,
            DeadLetterEventService deadLetterEventService,
            StatefulRedeliveryFailureInjector redeliveryFailureInjector,
            FraudConsumerMetrics metrics,
            Clock clock,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId,
            @Value("${fraud.consumer.slow-event-threshold:500ms}") Duration slowEventThreshold
    ) {
        this.processingLogService = processingLogService;
        this.recentTransactionWindowStore = recentTransactionWindowStore;
        this.fraudRuleEngine = fraudRuleEngine;
        this.fraudDetectionResultService = fraudDetectionResultService;
        this.deadLetterEventService = deadLetterEventService;
        this.redeliveryFailureInjector = redeliveryFailureInjector;
        this.metrics = metrics;
        this.clock = clock;
        this.consumerGroupId = consumerGroupId;
        this.slowEventThreshold = slowEventThreshold;
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

        TimedResult<ProcessingLogResult> processingLog = time(() -> metrics.recordProcessingLogLatency(
                () -> processingLogService.recordProcessedEvent(
                        message,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        consumerGroupId
                )
        ));
        ProcessingLogResult result = processingLog.result();

        TimedResult<Boolean> duplicatePrecheck = time(() -> metrics.recordResultPrecheckLatency(
                () -> fraudDetectionResultService.existsResultForEventId(message.eventId())
        ));
        if (duplicatePrecheck.result()) {
            acknowledgment.acknowledge();
            logSlowEventIfNeeded(
                    record,
                    message,
                    processingStartedAt,
                    processingLog.duration(),
                    duplicatePrecheck.duration(),
                    Duration.ZERO,
                    Duration.ZERO,
                    Duration.ZERO
            );
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

        redeliveryFailureInjector.failIfConfigured(StatefulRedeliveryFailurePoint.BEFORE_REDIS_UPDATE, message);
        TimedResult<RecentTransactionWindowResult> redisWindow = time(
                () -> recentTransactionWindowStore.recordAndGetWindow(message)
        );
        RecentTransactionWindowResult windowResult = redisWindow.result();
        redeliveryFailureInjector.failIfConfigured(
                StatefulRedeliveryFailurePoint.AFTER_REDIS_UPDATE_BEFORE_RESULT,
                message
        );
        FraudRuleEngineResult ruleResult;
        TimedResult<FraudRuleEngineResult> ruleProcessing;
        try {
            ruleProcessing = time(
                    () -> metrics.recordRuleProcessingLatency(() -> fraudRuleEngine.evaluate(message, windowResult))
            );
            ruleResult = ruleProcessing.result();
        } catch (RuntimeException exception) {
            recordDeadLetterAndAcknowledge(record, acknowledgment, FailureStage.RULE_ENGINE_ERROR, exception);
            return;
        }
        TimedResult<FraudDetectionResultSaveResult> resultSink = time(() -> metrics.recordResultSinkLatency(
                () -> fraudDetectionResultService.saveResult(message, ruleResult)
        ));
        FraudDetectionResultSaveResult saveResult = resultSink.result();
        recordDetectionMetrics(ruleResult, saveResult);
        if (!saveResult.duplicateSkipped()) {
            metrics.recordDetectionProcessingLatency(Duration.between(processingStartedAt, clock.instant()));
        }
        redeliveryFailureInjector.failIfConfigured(
                StatefulRedeliveryFailurePoint.AFTER_RESULT_SAVE_BEFORE_ACK,
                message
        );

        acknowledgment.acknowledge();
        logSlowEventIfNeeded(
                record,
                message,
                processingStartedAt,
                processingLog.duration(),
                duplicatePrecheck.duration(),
                redisWindow.duration(),
                ruleProcessing.duration(),
                resultSink.duration()
        );

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

    private <T> TimedResult<T> time(java.util.function.Supplier<T> supplier) {
        Instant startedAt = clock.instant();
        try {
            return new TimedResult<>(supplier.get(), Duration.between(startedAt, clock.instant()));
        } catch (RuntimeException exception) {
            Duration duration = Duration.between(startedAt, clock.instant());
            log.debug("consumer stage failed after {}ms", duration.toMillis(), exception);
            throw exception;
        }
    }

    private void logSlowEventIfNeeded(
            ConsumerRecord<String, TransactionEventMessage> record,
            TransactionEventMessage message,
            Instant processingStartedAt,
            Duration processingLogDuration,
            Duration duplicatePrecheckDuration,
            Duration redisDuration,
            Duration ruleDuration,
            Duration resultSinkDuration
    ) {
        Duration consumerServiceDuration = Duration.between(processingStartedAt, clock.instant());
        if (slowEventThreshold == null || consumerServiceDuration.compareTo(slowEventThreshold) <= 0) {
            return;
        }
        log.warn(
                "slow consumer event type=SLOW_EVENT traceId={} eventId={} topic={} partition={} offset={} consumerServiceMs={} processingLogMs={} duplicateGuardMs={} redisMs={} ruleMs={} resultSinkMs={}",
                message.traceId(),
                message.eventId(),
                record.topic(),
                record.partition(),
                record.offset(),
                consumerServiceDuration.toMillis(),
                processingLogDuration.toMillis(),
                duplicatePrecheckDuration.toMillis(),
                redisDuration.toMillis(),
                ruleDuration.toMillis(),
                resultSinkDuration.toMillis()
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

    private record TimedResult<T>(T result, Duration duration) {
    }
}
