package com.example.fraud.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record FraudAlertEventMessage(
        String schemaVersion,
        String eventId,
        String userId,
        RiskLevel riskLevel,
        int riskScore,
        List<FraudRuleCode> matchedRuleCodes,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime detectedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime alertCreatedAt,
        String traceId
) {
    public static final String CURRENT_SCHEMA_VERSION = "v1";

    public FraudAlertEventMessage {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
    }
}
