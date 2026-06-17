package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Operational summary response")
public record OperationSummaryResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        long acceptedEventCount,
        long fraudResultCount,
        long highRiskCount,
        long dlqPendingCount,
        long degradedResultCount,
        long duplicateSkippedCount
) {
}
