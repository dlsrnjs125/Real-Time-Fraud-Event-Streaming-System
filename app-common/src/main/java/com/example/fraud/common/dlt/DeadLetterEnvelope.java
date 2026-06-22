package com.example.fraud.common.dlt;

import com.example.fraud.common.event.TransactionEventMessage;
import java.time.OffsetDateTime;

public record DeadLetterEnvelope(
        String eventId,
        String traceId,
        String userId,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String failureStage,
        String errorType,
        String errorMessage,
        TransactionEventMessage payload,
        OffsetDateTime failedAt
) {
}
