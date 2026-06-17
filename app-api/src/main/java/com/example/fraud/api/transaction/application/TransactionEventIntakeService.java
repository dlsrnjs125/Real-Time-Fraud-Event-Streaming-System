package com.example.fraud.api.transaction.application;

import com.example.fraud.api.transaction.dto.TransactionEventAcceptedResponse;
import com.example.fraud.api.transaction.dto.TransactionEventReceiptResponse;
import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.api.transaction.kafka.TransactionEventProducer;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptEntity;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptRepository;
import com.example.fraud.common.event.TransactionEventMessage;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionEventIntakeService {

    private static final long MAX_FUTURE_EVENT_TIME_MINUTES = 5;

    private final TransactionEventReceiptRepository receiptRepository;
    private final TransactionEventMessageMapper messageMapper;
    private final TransactionEventProducer producer;
    private final Clock clock;

    public TransactionEventIntakeService(
            TransactionEventReceiptRepository receiptRepository,
            TransactionEventMessageMapper messageMapper,
            TransactionEventProducer producer,
            Clock clock
    ) {
        this.receiptRepository = receiptRepository;
        this.messageMapper = messageMapper;
        this.producer = producer;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = KafkaPublishFailedException.class)
    public TransactionEventAcceptedResponse accept(TransactionEventRequest request, String traceId) {
        if (receiptRepository.existsByEventId(request.eventId())) {
            throw new DuplicateTransactionEventException(request.eventId());
        }

        OffsetDateTime receivedAt = OffsetDateTime.now(clock);
        validateEventTime(request, receivedAt);

        TransactionEventReceiptEntity receipt = saveReceivedReceipt(request, traceId, receivedAt);
        TransactionEventMessage message = messageMapper.toMessage(receipt);

        try {
            producer.publish(message);
            receipt.markPublished();
            receiptRepository.save(receipt);
        } catch (KafkaPublishFailedException exception) {
            receipt.markPublishFailed(exception.getMessage());
            receiptRepository.save(receipt);
            throw exception;
        }

        return new TransactionEventAcceptedResponse(
                receipt.getEventId(),
                "ACCEPTED",
                receipt.getReceivedAt(),
                receipt.getTraceId()
        );
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
}
