package com.example.fraud.api.transaction.dto;

import com.example.fraud.common.event.TransactionEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Transaction event receipt lookup response")
public record TransactionEventReceiptResponse(
        String eventId,
        String userId,
        TransactionEventType eventType,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime eventTime,
        OffsetDateTime receivedAt,
        String traceId
) {
}
