package com.example.fraud.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransactionEventMessageSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesOffsetDateTimeAsIsoStringForKafkaJsonContract() throws Exception {
        TransactionEventMessage message = new TransactionEventMessage(
                "v1",
                "evt-serialization-001",
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
                "trace-serialization-001"
        );

        String json = objectMapper.writeValueAsString(message);

        assertThat(json).contains("\"eventTime\":\"2026-06-15T10:30:00+09:00\"");
        assertThat(json).contains("\"receivedAt\":\"2026-06-15T10:30:01+09:00\"");
    }
}
