package com.example.fraud.api.admin.dto;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Fraud detection result response")
public record FraudDetectionResultResponse(
        String eventId,
        String traceId,
        String userId,
        String accountId,
        int riskScore,
        RiskLevel riskLevel,
        FraudDecision decision,
        List<FraudRuleCode> matchedRules,
        String reason,
        OffsetDateTime detectedAt
) {
}
