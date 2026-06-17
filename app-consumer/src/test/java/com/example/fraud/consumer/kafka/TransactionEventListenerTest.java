package com.example.fraud.consumer.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.example.fraud.consumer.processing.EventProcessingLogService;
import com.example.fraud.consumer.processing.ProcessingLogResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class TransactionEventListenerTest {

    private final EventProcessingLogService processingLogService = mock(EventProcessingLogService.class);
    private final TransactionEventListener listener = new TransactionEventListener(
            processingLogService,
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

        listener.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
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
}
