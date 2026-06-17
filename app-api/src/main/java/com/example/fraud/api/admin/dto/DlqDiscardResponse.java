package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DLQ discard response")
public record DlqDiscardResponse(
        long dlqId,
        String status,
        String traceId
) {
}
