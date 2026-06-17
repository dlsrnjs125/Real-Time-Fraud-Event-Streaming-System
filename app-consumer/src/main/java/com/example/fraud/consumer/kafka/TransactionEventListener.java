package com.example.fraud.consumer.kafka;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.processing.EventProcessingLogService;
import com.example.fraud.consumer.processing.ProcessingLogResult;
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
    private final String consumerGroupId;

    public TransactionEventListener(
            EventProcessingLogService processingLogService,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId
    ) {
        this.processingLogService = processingLogService;
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

        acknowledgment.acknowledge();

        log.info(
                "transaction event consumed traceId={} eventId={} userId={} topic={} partition={} offset={} duplicateSkipped={}",
                message.traceId(),
                message.eventId(),
                message.userId(),
                record.topic(),
                record.partition(),
                record.offset(),
                result.duplicateSkipped()
        );
    }
}
