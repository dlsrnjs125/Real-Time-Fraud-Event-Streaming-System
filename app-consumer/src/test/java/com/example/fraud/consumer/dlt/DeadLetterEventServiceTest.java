package com.example.fraud.consumer.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.example.fraud.consumer.kafka.KafkaTopicNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(DeadLetterEventServiceTest.TestConfig.class)
class DeadLetterEventServiceTest {

    @Autowired
    private DeadLetterEventRepository repository;

    @Autowired
    private DeadLetterEventService service;

    @MockBean
    private DeadLetterEventPublisher publisher;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void savesFailureAsPendingDeadLetterEvent() {
        ConsumerRecord<String, TransactionEventMessage> record = record("evt-dlt-save-001", 0, 10L);

        DeadLetterEventEntity saved = service.recordFailure(
                record,
                FailureStage.RULE_ENGINE_ERROR,
                new IllegalStateException("rule failed")
        );

        assertThat(saved.getStatus()).isEqualTo(DeadLetterStatus.PENDING);
        assertThat(saved.getEventId()).isEqualTo("evt-dlt-save-001");
        assertThat(saved.getDltTopic()).isEqualTo(KafkaTopicNames.TRANSACTION_EVENTS_DLT);
        assertThat(saved.getFailureStage()).isEqualTo(FailureStage.RULE_ENGINE_ERROR);
        assertThat(saved.getErrorType()).isEqualTo("IllegalStateException");
        assertThat(saved.getPayloadJson()).contains("\"eventId\":\"evt-dlt-save-001\"");
    }

    @Test
    void duplicateSourceOffsetReturnsExistingRow() {
        ConsumerRecord<String, TransactionEventMessage> first = record("evt-dlt-duplicate-001", 1, 33L);
        ConsumerRecord<String, TransactionEventMessage> second = record("evt-dlt-duplicate-002", 1, 33L);

        DeadLetterEventEntity firstSaved = service.recordFailure(
                first,
                FailureStage.RULE_ENGINE_ERROR,
                new RuntimeException("first")
        );
        DeadLetterEventEntity duplicate = service.recordFailure(
                second,
                FailureStage.RULE_ENGINE_ERROR,
                new RuntimeException("second")
        );

        assertThat(duplicate.getId()).isEqualTo(firstSaved.getId());
        assertThat(repository.findAll()).hasSize(1);
        assertThat(duplicate.getEventId()).isEqualTo("evt-dlt-duplicate-001");
    }

    @Test
    void truncatesLongErrorMessage() {
        ConsumerRecord<String, TransactionEventMessage> record = record("evt-dlt-long-error", 2, 44L);

        DeadLetterEventEntity saved = service.recordFailure(
                record,
                FailureStage.RULE_ENGINE_ERROR,
                new RuntimeException("x".repeat(1200))
        );

        assertThat(saved.getErrorMessage()).hasSize(1000);
    }

    private ConsumerRecord<String, TransactionEventMessage> record(String eventId, int partition, long offset) {
        TransactionEventMessage message = message(eventId);
        return new ConsumerRecord<>(
                KafkaTopicNames.TRANSACTION_EVENTS,
                partition,
                offset,
                message.userId(),
                message
        );
    }

    private TransactionEventMessage message(String eventId) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-22T10:00:00Z");
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

    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-22T10:00:02Z"), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        DeadLetterEventService deadLetterEventService(
                DeadLetterEventRepository repository,
                DeadLetterEventPublisher publisher,
                ObjectMapper objectMapper,
                Clock clock
        ) {
            return new DeadLetterEventService(repository, publisher, objectMapper, clock);
        }
    }
}
