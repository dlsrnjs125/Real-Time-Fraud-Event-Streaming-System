package com.example.fraud.api.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Transaction event accepted response")
public record TransactionEventAcceptedResponse(
        @Schema(example = "evt-20260615-000001")
        String eventId,

        @Schema(example = "ACCEPTED")
        String status,

        @Schema(example = "2026-06-15T10:30:01+09:00")
        OffsetDateTime receivedAt,

        @Schema(example = "trace-20260615-000001")
        String traceId
) {
}
