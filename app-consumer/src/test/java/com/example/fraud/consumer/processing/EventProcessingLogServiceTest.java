package com.example.fraud.consumer.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(EventProcessingLogServiceTest.TestConfig.class)
class EventProcessingLogServiceTest {

    @Autowired
    private EventProcessingLogRepository repository;

    @Autowired
    private EventProcessingLogService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void savesProcessingLogForTransactionEventMessage() {
        TransactionEventMessage message = message("evt-processing-log-001");

        ProcessingLogResult result = service.recordProcessedEvent(
                message,
                "transaction-events",
                1,
                10L,
                "fraud-event-consumer"
        );

        assertThat(result.duplicateSkipped()).isFalse();
        var saved = repository.findByEventIdOrderByProcessedAtDesc("evt-processing-log-001");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEventId()).isEqualTo("evt-processing-log-001");
        assertThat(saved.get(0).getTraceId()).isEqualTo("trace-evt-processing-log-001");
        assertThat(saved.get(0).getUserId()).isEqualTo("user-1001");
        assertThat(saved.get(0).getTopic()).isEqualTo("transaction-events");
        assertThat(saved.get(0).getPartitionNo()).isEqualTo(1);
        assertThat(saved.get(0).getOffsetNo()).isEqualTo(10L);
        assertThat(saved.get(0).getConsumerGroupId()).isEqualTo("fraud-event-consumer");
        assertThat(saved.get(0).getStatus()).isEqualTo(EventProcessingStatus.PROCESSED);
    }

    @Test
    void duplicateTopicPartitionOffsetDoesNotCreateAnotherLog() {
        TransactionEventMessage first = message("evt-processing-log-duplicate-001");
        TransactionEventMessage second = message("evt-processing-log-duplicate-002");

        ProcessingLogResult firstResult = service.recordProcessedEvent(
                first,
                "transaction-events",
                0,
                99L,
                "fraud-event-consumer"
        );
        ProcessingLogResult secondResult = service.recordProcessedEvent(
                second,
                "transaction-events",
                0,
                99L,
                "fraud-event-consumer"
        );

        assertThat(firstResult.duplicateSkipped()).isFalse();
        assertThat(secondResult.duplicateSkipped()).isTrue();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void sameEventIdWithDifferentOffsetsCreatesSeparateLogs() {
        TransactionEventMessage message = message("evt-processing-log-same-event");

        ProcessingLogResult firstResult = service.recordProcessedEvent(
                message,
                "transaction-events",
                0,
                100L,
                "fraud-event-consumer"
        );
        ProcessingLogResult secondResult = service.recordProcessedEvent(
                message,
                "transaction-events",
                0,
                101L,
                "fraud-event-consumer"
        );

        assertThat(firstResult.duplicateSkipped()).isFalse();
        assertThat(secondResult.duplicateSkipped()).isFalse();
        assertThat(repository.findByEventIdOrderByProcessedAtDesc("evt-processing-log-same-event"))
                .hasSize(2);
    }

    @Test
    void findsLogsByEventIdWithLatestProcessedAtFirst() {
        insertProcessingLog(
                "evt-processing-log-order",
                "transaction-events",
                0,
                200L,
                OffsetDateTime.parse("2026-06-17T10:00:02Z")
        );
        insertProcessingLog(
                "evt-processing-log-order",
                "transaction-events",
                0,
                201L,
                OffsetDateTime.parse("2026-06-17T10:00:05Z")
        );

        assertThat(repository.findByEventIdOrderByProcessedAtDesc("evt-processing-log-order"))
                .extracting(EventProcessingLogEntity::getOffsetNo)
                .containsExactly(201L, 200L);
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateOffsetDirectly() {
        OffsetDateTime processedAt = OffsetDateTime.parse("2026-06-17T10:00:02Z");

        insertProcessingLog(
                "evt-processing-log-constraint-001",
                "transaction-events",
                3,
                123L,
                processedAt
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertProcessingLog(
                        "evt-processing-log-constraint-002",
                        "transaction-events",
                        3,
                        123L,
                        processedAt
                ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertProcessingLog(
            String eventId,
            String topic,
            int partitionNo,
            long offsetNo,
            OffsetDateTime processedAt
    ) {
        jdbcTemplate.update("""
                        insert into event_processing_logs (
                            event_id,
                            trace_id,
                            user_id,
                            topic,
                            partition_no,
                            offset_no,
                            consumer_group_id,
                            status,
                            received_at,
                            processed_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                "trace-" + eventId,
                "user-1001",
                topic,
                partitionNo,
                offsetNo,
                "fraud-event-consumer",
                "PROCESSED",
                processedAt.minusSeconds(1),
                processedAt,
                processedAt,
                processedAt
        );
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

    static class TestConfig {

        @org.springframework.context.annotation.Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-17T10:00:02Z"), ZoneOffset.UTC);
        }

        @org.springframework.context.annotation.Bean
        EventProcessingLogService eventProcessingLogService(
                EventProcessingLogRepository repository,
                Clock clock
        ) {
            return new EventProcessingLogService(repository, clock);
        }
    }
}
