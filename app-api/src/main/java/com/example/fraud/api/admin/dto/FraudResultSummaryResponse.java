package com.example.fraud.api.admin.dto;

import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Fraud result summary")
public record FraudResultSummaryResponse(
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<FraudRuleCode> matchedRuleCodes,
        List<FraudRuleCode> skippedRuleCodes,
        boolean degraded,
        OffsetDateTime detectedAt,
        long detectionLatencyMs,
        String traceId
) {
}
