package com.example.fraud.api.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TransactionEventMessageMapperTest {

    private final TransactionEventMessageMapper mapper = new TransactionEventMessageMapper();

    @Test
    void mapsRequestToReceiptAndMessageContract() {
        TransactionEventRequest request = new TransactionEventRequest(
                "evt-map-001",
                "user-1001",
                "acc-3001",
                TransactionEventType.PAYMENT,
                new BigDecimal("1500000"),
                "KRW",
                "merchant-777",
                "device-abc",
                "KR",
                OffsetDateTime.parse("2026-06-15T10:30:00+09:00")
        );
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-06-15T10:30:01+09:00");

        var receipt = mapper.toReceipt(request, "trace-map-001", receivedAt);
        var message = mapper.toMessage(receipt);

        assertThat(message.schemaVersion()).isEqualTo("v1");
        assertThat(message.eventId()).isEqualTo("evt-map-001");
        assertThat(message.userId()).isEqualTo("user-1001");
        assertThat(message.receivedAt()).isEqualTo(receivedAt);
        assertThat(message.traceId()).isEqualTo("trace-map-001");
    }
}
