package com.example.fraud.consumer.kafka;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.fraud.FraudDetectionResultSaveResult;
import com.example.fraud.consumer.fraud.FraudDetectionResultService;
import com.example.fraud.consumer.metrics.FraudConsumerMetrics;
import com.example.fraud.consumer.processing.EventProcessingLogService;
import com.example.fraud.consumer.processing.ProcessingLogResult;
import com.example.fraud.consumer.redis.RecentTransactionWindowResult;
import com.example.fraud.consumer.redis.RecentTransactionWindowStore;
import com.example.fraud.consumer.rule.FraudRuleEngine;
import com.example.fraud.consumer.rule.FraudRuleEngineResult;
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
    private final FraudConsumerMetrics metrics;
    private final String consumerGroupId;

    public TransactionEventListener(
            EventProcessingLogService processingLogService,
            RecentTransactionWindowStore recentTransactionWindowStore,
            FraudRuleEngine fraudRuleEngine,
            FraudDetectionResultService fraudDetectionResultService,
            FraudConsumerMetrics metrics,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId
    ) {
        this.processingLogService = processingLogService;
        this.recentTransactionWindowStore = recentTransactionWindowStore;
        this.fraudRuleEngine = fraudRuleEngine;
        this.fraudDetectionResultService = fraudDetectionResultService;
        this.metrics = metrics;
        this.consumerGroupId = consumerGroupId;
    }

    @KafkaListener(topics = KafkaTopicNames.TRANSACTION_EVENTS)
    public void onMessage(ConsumerRecord<String, TransactionEventMessage> record, Acknowledgment acknowledgment) {
        TransactionEventMessage message = record.value();

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
        FraudRuleEngineResult ruleResult = fraudRuleEngine.evaluate(message, windowResult);
        FraudDetectionResultSaveResult saveResult = fraudDetectionResultService.saveResult(message, ruleResult);
        recordDetectionMetrics(ruleResult, saveResult);

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
