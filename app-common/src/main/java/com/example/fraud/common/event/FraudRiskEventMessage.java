package com.example.fraud.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record FraudRiskEventMessage(
        String schemaVersion,
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<FraudRuleCode> matchedRuleCodes,
        List<FraudRuleCode> skippedRuleCodes,
        boolean degraded,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime detectedAt,
        String traceId
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";

    public FraudRiskEventMessage {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
    }
}
