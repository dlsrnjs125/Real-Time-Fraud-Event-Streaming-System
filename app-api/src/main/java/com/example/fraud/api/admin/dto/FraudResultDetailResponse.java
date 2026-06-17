package com.example.fraud.api.admin.dto;

import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Fraud result detail")
public record FraudResultDetailResponse(
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<FraudRuleCode> matchedRuleCodes,
        List<FraudRuleCode> skippedRuleCodes,
        boolean degraded,
        List<FraudRuleResultResponse> ruleResults,
        OffsetDateTime eventTime,
        OffsetDateTime receivedAt,
        OffsetDateTime detectedAt,
        long detectionLatencyMs,
        long endToEndLatencyMs,
        String traceId
) {
}
