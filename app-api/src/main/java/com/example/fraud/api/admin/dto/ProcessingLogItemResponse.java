package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Event processing log item")
public record ProcessingLogItemResponse(
        String eventId,
        String traceId,
        String userId,
        String topic,
        int partition,
        long offset,
        String consumerGroupId,
        String status,
        OffsetDateTime receivedAt,
        OffsetDateTime processedAt,
        String errorMessage
) {
}
