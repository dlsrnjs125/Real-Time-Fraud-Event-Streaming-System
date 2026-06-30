package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Stored fraud result count grouped by ruleVersion")
public record FraudRuleVersionSummaryResponse(
        @Schema(description = "Counts for non-null per-result ruleVersion values")
        Map<String, Long> resultCounts,
        @Schema(description = "Stored result count where ruleVersion is null for legacy rows")
        long legacyMissingResults
) {
}
