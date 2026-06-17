package com.example.fraud.api.admin.dto;

import com.example.fraud.common.event.FraudRuleCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rule execution result in a fraud result")
public record FraudRuleResultResponse(
        FraudRuleCode ruleCode,
        boolean matched,
        boolean skipped,
        int score,
        String reason
) {
}
