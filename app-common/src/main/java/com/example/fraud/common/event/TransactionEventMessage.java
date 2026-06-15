package com.example.fraud.common.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionEventMessage(
        String eventId,
        String userId,
        String accountId,
        BigDecimal amount,
        String merchantId,
        String deviceId,
        String location,
        OffsetDateTime eventTime,
        String traceId
) {
}
