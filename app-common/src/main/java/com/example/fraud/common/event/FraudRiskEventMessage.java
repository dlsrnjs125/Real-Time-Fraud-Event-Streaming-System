package com.example.fraud.common.event;

import java.time.OffsetDateTime;
import java.util.List;

public record FraudRiskEventMessage(
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<String> matchedRules,
        boolean degraded,
        OffsetDateTime detectedAt,
        String traceId
) {
}
