package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudRuleCode;

public record FraudRuleEvaluation(
        FraudRuleCode ruleCode,
        boolean matched,
        int score,
        String reason
) {
}
