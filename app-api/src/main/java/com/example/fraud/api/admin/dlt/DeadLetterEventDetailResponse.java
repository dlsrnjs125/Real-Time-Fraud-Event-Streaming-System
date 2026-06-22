package com.example.fraud.api.admin.dlt;

import java.time.OffsetDateTime;

public record DeadLetterEventDetailResponse(
        long id,
        String eventId,
        String traceId,
        String userId,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String dltTopic,
        String failureStage,
        String errorType,
        String errorMessage,
        String payloadJson,
        DeadLetterStatus status,
        int reprocessAttempts,
        OffsetDateTime lastReprocessedAt,
        OffsetDateTime discardedAt,
        String discardReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
