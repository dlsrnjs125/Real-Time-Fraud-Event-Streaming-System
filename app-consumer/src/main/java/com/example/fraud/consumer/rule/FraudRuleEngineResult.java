package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import java.util.List;

public record FraudRuleEngineResult(
        String ruleVersion,
        int riskScore,
        RiskLevel riskLevel,
        FraudDecision decision,
        List<FraudRuleCode> matchedRules,
        List<FraudRuleCode> skippedRules,
        boolean degraded,
        String reason
) {
}
