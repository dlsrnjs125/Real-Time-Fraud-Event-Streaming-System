package com.example.fraud.consumer.dlt;

import com.example.fraud.common.dlt.DeadLetterEnvelope;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.kafka.KafkaTopicNames;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterEventService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final DeadLetterEventRepository repository;
    private final DeadLetterEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeadLetterEventService(
            DeadLetterEventRepository repository,
            DeadLetterEventPublisher publisher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DeadLetterEventEntity recordFailure(
            ConsumerRecord<String, TransactionEventMessage> record,
            FailureStage failureStage,
            Throwable failure
    ) {
        return repository.findBySourceTopicAndSourcePartitionAndSourceOffset(
                        record.topic(),
                        record.partition(),
                        record.offset()
                )
                .orElseGet(() -> saveNew(record, failureStage, failure));
    }

    public void publish(DeadLetterEventEntity event, TransactionEventMessage payload, OffsetDateTime failedAt) {
        publisher.publish(new DeadLetterEnvelope(
                event.getEventId(),
                event.getTraceId(),
                event.getUserId(),
                event.getSourceTopic(),
                event.getSourcePartition(),
                event.getSourceOffset(),
                event.getFailureStage().name(),
                event.getErrorType(),
                event.getErrorMessage(),
                payload,
                failedAt
        ), event.getDltTopic());
    }

    private DeadLetterEventEntity saveNew(
            ConsumerRecord<String, TransactionEventMessage> record,
            FailureStage failureStage,
            Throwable failure
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        DeadLetterEventEntity entity = DeadLetterEventEntity.pending(
                record.value(),
                record.topic(),
                record.partition(),
                record.offset(),
                KafkaTopicNames.TRANSACTION_EVENTS_DLT,
                failureStage,
                sanitizeErrorType(failure),
                sanitizeErrorMessage(failure),
                sanitizePayload(record.value()),
                now
        );
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            return repository.findBySourceTopicAndSourcePartitionAndSourceOffset(
                            record.topic(),
                            record.partition(),
                            record.offset()
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private String sanitizeErrorType(Throwable failure) {
        return failure.getClass().getSimpleName();
    }

    private String sanitizeErrorMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return message;
    }

    private String sanitizePayload(TransactionEventMessage message) {
        // Phase 9 stores synthetic identifiers. Apply masking/redaction here before production use.
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize dead letter payload", exception);
        }
    }
}
