package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "DLQ event summary")
public record DlqEventSummaryResponse(
        long dlqId,
        String eventId,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String failureReason,
        String status,
        String payloadHash,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
