package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FraudRuleEngine {

    private static final int MAX_RISK_SCORE = 100;

    private final List<FraudRule> rules;

    public FraudRuleEngine(List<FraudRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(rule -> rule.getClass().getSimpleName()))
                .toList();
    }

    public FraudRuleEngineResult evaluate(TransactionEventMessage message) {
        List<FraudRuleEvaluation> evaluations = rules.stream()
                .map(rule -> rule.evaluate(message))
                .toList();

        int riskScore = Math.min(
                MAX_RISK_SCORE,
                evaluations.stream()
                        .filter(FraudRuleEvaluation::matched)
                        .mapToInt(FraudRuleEvaluation::score)
                        .sum()
        );
        RiskLevel riskLevel = toRiskLevel(riskScore);
        FraudDecision decision = toDecision(riskLevel);
        List<FraudRuleCode> matchedRules = evaluations.stream()
                .filter(FraudRuleEvaluation::matched)
                .map(FraudRuleEvaluation::ruleCode)
                .toList();
        String reason = evaluations.stream()
                .filter(FraudRuleEvaluation::matched)
                .map(FraudRuleEvaluation::reason)
                .reduce((left, right) -> left + "; " + right)
                .orElse("No fraud rule matched");

        return new FraudRuleEngineResult(riskScore, riskLevel, decision, matchedRules, reason);
    }

    private RiskLevel toRiskLevel(int riskScore) {
        if (riskScore <= 29) {
            return RiskLevel.LOW;
        }
        if (riskScore <= 69) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private FraudDecision toDecision(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> FraudDecision.APPROVE;
            case MEDIUM -> FraudDecision.REVIEW;
            case HIGH -> FraudDecision.BLOCK;
        };
    }
}
