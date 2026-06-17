package com.example.fraud.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionEventMessage(
        String schemaVersion,
        String eventId,
        String userId,
        String accountId,
        TransactionEventType eventType,
        BigDecimal amount,
        String currency,
        String merchantId,
        String deviceId,
        String location,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime eventTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime receivedAt,
        String traceId
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";

    public TransactionEventMessage {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
    }
}
