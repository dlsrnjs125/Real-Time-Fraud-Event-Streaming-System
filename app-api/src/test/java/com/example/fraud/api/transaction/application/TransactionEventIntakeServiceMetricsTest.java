package com.example.fraud.api.transaction.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.api.transaction.kafka.TransactionEventProducer;
import com.example.fraud.api.transaction.metrics.TransactionIntakeMetrics;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptEntity;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptRepository;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TransactionEventIntakeServiceMetricsTest {

    private final TransactionEventReceiptRepository repository = mock(TransactionEventReceiptRepository.class);
    private final TransactionEventProducer producer = mock(TransactionEventProducer.class);
    private final TransactionIntakeMetrics metrics = mock(TransactionIntakeMetrics.class);
    private final TransactionEventMessageMapper mapper = new TransactionEventMessageMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
    private final TransactionEventIntakeService service = new TransactionEventIntakeService(
            repository,
            mapper,
            producer,
            metrics,
            clock
    );

    @Test
    void recordsSuccessfulPublishOutcome() {
        when(repository.existsByEventId("event-1")).thenReturn(false);
        when(repository.saveAndFlush(any(TransactionEventReceiptEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.accept(request("event-1"), "trace-1");

        verify(metrics).recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.SUCCESS);
        verify(metrics, never()).recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.FAILURE);
    }

    @Test
    void recordsFailedPublishOutcome() {
        when(repository.existsByEventId("event-2")).thenReturn(false);
        when(repository.saveAndFlush(any(TransactionEventReceiptEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new KafkaPublishFailedException(new RuntimeException("broker unavailable")))
                .when(producer).publish(any(TransactionEventMessage.class));

        assertThatThrownBy(() -> service.accept(request("event-2"), "trace-2"))
                .isInstanceOf(KafkaPublishFailedException.class);

        verify(metrics).recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.FAILURE);
        verify(metrics, never()).recordAfterCommit(TransactionIntakeMetrics.PublishOutcome.SUCCESS);
    }

    @Test
    void duplicateRequestDoesNotRecordStreamCounters() {
        when(repository.existsByEventId("event-duplicate")).thenReturn(true);

        assertThatThrownBy(() -> service.accept(request("event-duplicate"), "trace-duplicate"))
                .isInstanceOf(DuplicateTransactionEventException.class);

        verify(metrics, never()).recordAfterCommit(any());
        verify(producer, never()).publish(any());
    }

    private TransactionEventRequest request(String eventId) {
        return new TransactionEventRequest(
                eventId,
                "user-1",
                "account-1",
                TransactionEventType.PAYMENT,
                new BigDecimal("1000"),
                "KRW",
                "merchant-1",
                "device-1",
                "KR",
                OffsetDateTime.parse("2026-08-19T00:00:00Z")
        );
    }
}
