package com.example.fraud.api.admin.dto;

import com.example.fraud.common.event.FraudRuleCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fraud rule contract response")
public record FraudRuleResponse(
        FraudRuleCode ruleCode,
        boolean enabled,
        int score,
        String condition,
        boolean requiresRedis
) {
}
