package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Event processing log item")
public record ProcessingLogItemResponse(
        String topic,
        int partitionNo,
        long offsetNo,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        long processingLatencyMs,
        String errorMessage
) {
}
