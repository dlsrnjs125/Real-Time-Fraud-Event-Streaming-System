package com.example.fraud.common.event;

import java.time.OffsetDateTime;
import java.util.List;

public record FraudRiskEventMessage(
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<String> matchedRuleCodes,
        List<String> skippedRuleCodes,
        boolean degraded,
        OffsetDateTime detectedAt,
        String traceId
) {
}
