package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DLQ reprocess response")
public record DlqReprocessResponse(
        long dlqId,
        String status,
        String reprocessAttemptId,
        String traceId
) {
}
