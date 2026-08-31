package com.example.fraud.api.admin.dlt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fraud.api.kafka.KafkaTopicNames;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class DeadLetterReprocessPublisherTest {

    @Test
    void republishesToOriginalLiveSourceTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, TransactionEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        TransactionEventMessage message = message("evt-dlt-reprocess-live");
        when(kafkaTemplate.send(KafkaTopicNames.TRANSACTION_EVENTS, "user-1001", message))
                .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterReprocessPublisher publisher = new DeadLetterReprocessPublisher(kafkaTemplate);

        publisher.publish(message, KafkaTopicNames.TRANSACTION_EVENTS);

        verify(kafkaTemplate).send(KafkaTopicNames.TRANSACTION_EVENTS, "user-1001", message);
    }

    @Test
    void republishesToOriginalReplaySourceTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, TransactionEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        TransactionEventMessage message = message("evt-dlt-reprocess-replay");
        when(kafkaTemplate.send(KafkaTopicNames.TRANSACTION_EVENTS_REPLAY, "user-1001", message))
                .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterReprocessPublisher publisher = new DeadLetterReprocessPublisher(kafkaTemplate);

        publisher.publish(message, KafkaTopicNames.TRANSACTION_EVENTS_REPLAY);

        verify(kafkaTemplate).send(KafkaTopicNames.TRANSACTION_EVENTS_REPLAY, "user-1001", message);
    }

    @Test
    void rejectsUnsupportedSourceTopicBeforePublishing() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, TransactionEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterReprocessPublisher publisher = new DeadLetterReprocessPublisher(kafkaTemplate);

        assertThatThrownBy(() -> publisher.publish(message("evt-dlt-reprocess-invalid"), "unknown-topic"))
                .isInstanceOf(DeadLetterStateConflictException.class);
        verify(kafkaTemplate, never()).send("unknown-topic", "user-1001", message("evt-dlt-reprocess-invalid"));
    }

    private TransactionEventMessage message(String eventId) {
        return new TransactionEventMessage(
                "v1",
                eventId,
                "user-1001",
                "acc-1001",
                TransactionEventType.PAYMENT,
                BigDecimal.valueOf(120_000),
                "KRW",
                "merchant-001",
                "device-001",
                "SEOUL",
                OffsetDateTime.parse("2026-06-22T10:00:00Z"),
                OffsetDateTime.parse("2026-06-22T10:00:01Z"),
                "trace-" + eventId
        );
    }
}
