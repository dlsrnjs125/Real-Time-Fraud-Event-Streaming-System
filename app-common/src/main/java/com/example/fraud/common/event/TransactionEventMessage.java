package com.example.fraud.common.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionEventMessage(
        String schemaVersion,
        String eventId,
        String userId,
        String accountId,
        String eventType,
        BigDecimal amount,
        String currency,
        String merchantId,
        String deviceId,
        String location,
        OffsetDateTime eventTime,
        OffsetDateTime receivedAt,
        String traceId
) {
}
