package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import java.util.List;

public record FraudRuleEngineResult(
        int riskScore,
        RiskLevel riskLevel,
        FraudDecision decision,
        List<FraudRuleCode> matchedRules,
        String reason
) {
}
