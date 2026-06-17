package com.example.fraud.common.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TransactionEventMessageTest {

    @Test
    void transactionEventMessageUsesTypedContractFields() {
        TransactionEventMessage message = new TransactionEventMessage(
                null,
                "evt-20260615-000001",
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
                "trace-20260615-000001"
        );

        assertEquals("v1", message.schemaVersion());
        assertEquals(TransactionEventType.PAYMENT, message.eventType());
        assertEquals(0, message.amount().compareTo(new BigDecimal("1500000")));
    }
}
