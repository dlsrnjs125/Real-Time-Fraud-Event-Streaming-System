package com.example.fraud.api.transaction.application;

import com.example.fraud.api.transaction.dto.TransactionEventAcceptedResponse;
import com.example.fraud.api.transaction.dto.TransactionEventReceiptResponse;
import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.api.transaction.kafka.TransactionEventProducer;
import com.example.fraud.api.transaction.metrics.TransactionIntakeMetrics;
import com.example.fraud.api.transaction.metrics.TransactionIntakeMetrics.PublishOutcome;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptEntity;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptRepository;
import com.example.fraud.common.event.TransactionEventMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionEventIntakeService {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventIntakeService.class);
    private static final long MAX_FUTURE_EVENT_TIME_MINUTES = 5;

    private final TransactionEventReceiptRepository receiptRepository;
    private final TransactionEventMessageMapper messageMapper;
    private final TransactionEventProducer producer;
    private final TransactionIntakeMetrics metrics;
    private final Clock clock;
    private final Duration slowIntakeThreshold;

    public TransactionEventIntakeService(
            TransactionEventReceiptRepository receiptRepository,
            TransactionEventMessageMapper messageMapper,
            TransactionEventProducer producer,
            TransactionIntakeMetrics metrics,
            Clock clock,
            @Value("${fraud.api.slow-intake-threshold:500ms}") Duration slowIntakeThreshold
    ) {
        this.receiptRepository = receiptRepository;
        this.messageMapper = messageMapper;
        this.producer = producer;
        this.metrics = metrics;
        this.clock = clock;
        this.slowIntakeThreshold = slowIntakeThreshold;
    }

    @Transactional(noRollbackFor = KafkaPublishFailedException.class)
    public TransactionEventAcceptedResponse accept(TransactionEventRequest request, String traceId) {
        long intakeStartedAt = System.nanoTime();
        Duration receiptPersistenceDuration = Duration.ZERO;
        Duration kafkaPublishWaitDuration = Duration.ZERO;
        Duration statusUpdateDuration = Duration.ZERO;
        String outcome = "UNKNOWN";
        try {
            if (receiptRepository.existsByEventId(request.eventId())) {
                outcome = "DUPLICATE";
                throw new DuplicateTransactionEventException(request.eventId());
            }

            OffsetDateTime receivedAt = OffsetDateTime.now(clock);
            validateEventTime(request, receivedAt);

            TimedResult<TransactionEventReceiptEntity> receiptResult = time(
                    () -> saveReceivedReceipt(request, traceId, receivedAt)
            );
            receiptPersistenceDuration = receiptResult.duration();
            metrics.recordReceiptPersistenceLatency(receiptPersistenceDuration);
            TransactionEventReceiptEntity receipt = receiptResult.result();
            TransactionEventMessage message = messageMapper.toMessage(receipt);

            try {
                long publishStartedAt = System.nanoTime();
                try {
                    producer.publish(message);
                } finally {
                    kafkaPublishWaitDuration = elapsed(publishStartedAt);
                    metrics.recordKafkaPublishWaitLatency(kafkaPublishWaitDuration);
                }
                TimedResult<TransactionEventReceiptEntity> statusUpdate = time(() -> {
                    receipt.markPublished();
                    return receiptRepository.save(receipt);
                });
                statusUpdateDuration = statusUpdate.duration();
                metrics.recordReceiptStatusUpdateLatency(statusUpdateDuration);
                metrics.recordAfterCommit(PublishOutcome.SUCCESS);
                outcome = "SUCCESS";
            } catch (KafkaPublishFailedException exception) {
                TimedResult<TransactionEventReceiptEntity> statusUpdate = time(() -> {
                    receipt.markPublishFailed(exception.getMessage());
                    return receiptRepository.save(receipt);
                });
                statusUpdateDuration = statusUpdate.duration();
                metrics.recordReceiptStatusUpdateLatency(statusUpdateDuration);
                metrics.recordAfterCommit(PublishOutcome.FAILURE);
                outcome = "KAFKA_PUBLISH_FAILED";
                throw exception;
            }

            return new TransactionEventAcceptedResponse(
                    receipt.getEventId(),
                    "ACCEPTED",
                    receipt.getReceivedAt(),
                    receipt.getTraceId()
            );
        } finally {
            Duration intakeDuration = elapsed(intakeStartedAt);
            metrics.recordApiIntakeServiceLatency(intakeDuration);
            logSlowIntakeIfNeeded(
                    request,
                    traceId,
                    outcome,
                    intakeDuration,
                    receiptPersistenceDuration,
                    kafkaPublishWaitDuration,
                    statusUpdateDuration
            );
        }
    }

    @Transactional(readOnly = true)
    public TransactionEventReceiptResponse getReceipt(String eventId) {
        TransactionEventReceiptEntity receipt = receiptRepository.findByEventId(eventId)
                .orElseThrow(() -> new TransactionEventNotFoundException(eventId));

        return new TransactionEventReceiptResponse(
                receipt.getEventId(),
                receipt.getUserId(),
                receipt.getEventType(),
                receipt.getAmount(),
                receipt.getCurrency(),
                receipt.getStatus().name(),
                receipt.getEventTime(),
                receipt.getReceivedAt(),
                receipt.getTraceId()
        );
    }

    private TransactionEventReceiptEntity saveReceivedReceipt(
            TransactionEventRequest request,
            String traceId,
            OffsetDateTime receivedAt
    ) {
        try {
            return receiptRepository.saveAndFlush(messageMapper.toReceipt(request, traceId, receivedAt));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateTransactionEventException(request.eventId());
        }
    }

    private void validateEventTime(TransactionEventRequest request, OffsetDateTime receivedAt) {
        if (request.eventTime().isAfter(receivedAt.plusMinutes(MAX_FUTURE_EVENT_TIME_MINUTES))) {
            throw new InvalidTransactionEventException("eventTime must not be more than 5 minutes in the future");
        }
    }

    private <T> TimedResult<T> time(java.util.function.Supplier<T> supplier) {
        long startedAt = System.nanoTime();
        return new TimedResult<>(supplier.get(), elapsed(startedAt));
    }

    private Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    private void logSlowIntakeIfNeeded(
            TransactionEventRequest request,
            String traceId,
            String outcome,
            Duration intakeDuration,
            Duration receiptPersistenceDuration,
            Duration kafkaPublishWaitDuration,
            Duration statusUpdateDuration
    ) {
        if (slowIntakeThreshold == null || intakeDuration.compareTo(slowIntakeThreshold) <= 0) {
            return;
        }
        log.warn(
                "slow api intake type=SLOW_INTAKE traceId={} eventId={} intakeServiceMs={} receiptPersistenceMs={} kafkaPublishWaitMs={} statusUpdateMs={} outcome={}",
                traceId,
                request.eventId(),
                intakeDuration.toMillis(),
                receiptPersistenceDuration.toMillis(),
                kafkaPublishWaitDuration.toMillis(),
                statusUpdateDuration.toMillis(),
                outcome
        );
    }

    private record TimedResult<T>(T result, Duration duration) {
    }
}
