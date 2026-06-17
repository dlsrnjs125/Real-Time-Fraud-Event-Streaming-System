package com.example.fraud.api.transaction.kafka;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

class KafkaTransactionEventProducerTest {

    @Test
    void publishesTransactionEventWithUserIdAsKafkaKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, TransactionEventMessage> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaTransactionEventProducer producer = new KafkaTransactionEventProducer(kafkaTemplate);
        TransactionEventMessage message = new TransactionEventMessage(
                "v1",
                "evt-kafka-001",
                "user-1001",
                "acc-3001",
                TransactionEventType.PAYMENT,
                new BigDecimal("1500000"),
                "KRW",
                "merchant-777",
                "device-abc",
                "KR",
                OffsetDateTime.parse("2026-06-15T10:30:00+09:00"),
                OffsetDateTime.parse("2026-06-15T10:30:01+09:00"),
                "trace-kafka-001"
        );
        when(kafkaTemplate.send(KafkaTopicNames.TRANSACTION_EVENTS, "user-1001", message))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(message);

        verify(kafkaTemplate).send(eq(KafkaTopicNames.TRANSACTION_EVENTS), eq("user-1001"), eq(message));
    }
}
