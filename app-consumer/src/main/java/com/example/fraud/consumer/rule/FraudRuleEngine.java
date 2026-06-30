package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.redis.RecentTransactionWindowResult;
import com.example.fraud.consumer.redis.SlidingWindowProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FraudRuleEngine {

    private static final int MAX_RISK_SCORE = 100;
    private static final int RAPID_TRANSACTION_COUNT_SCORE = 30;
    private static final int WINDOW_AMOUNT_SUM_SCORE = 40;

    private final List<FraudRule> rules;
    private final SlidingWindowProperties slidingWindowProperties;

    public FraudRuleEngine(List<FraudRule> rules, SlidingWindowProperties slidingWindowProperties) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(rule -> rule.getClass().getSimpleName()))
                .toList();
        this.slidingWindowProperties = slidingWindowProperties;
    }

    public FraudRuleEngineResult evaluate(TransactionEventMessage message) {
        return evaluate(message, null);
    }

    public FraudRuleEngineResult evaluate(
            TransactionEventMessage message,
            RecentTransactionWindowResult windowResult
    ) {
        List<FraudRuleEvaluation> evaluations = rules.stream()
                .map(rule -> rule.evaluate(message))
                .toList();
        List<FraudRuleEvaluation> statefulEvaluations = evaluateStatefulRules(windowResult);
        List<FraudRuleEvaluation> allEvaluations = new ArrayList<>();
        allEvaluations.addAll(evaluations);
        allEvaluations.addAll(statefulEvaluations);

        int riskScore = Math.min(
                MAX_RISK_SCORE,
                allEvaluations.stream()
                        .filter(FraudRuleEvaluation::matched)
                        .mapToInt(FraudRuleEvaluation::score)
                        .sum()
        );
        RiskLevel riskLevel = toRiskLevel(riskScore);
        FraudDecision decision = toDecision(riskLevel);
        List<FraudRuleCode> matchedRules = allEvaluations.stream()
                .filter(FraudRuleEvaluation::matched)
                .map(FraudRuleEvaluation::ruleCode)
                .toList();
        List<FraudRuleCode> skippedRules = windowResult != null && windowResult.degraded()
                ? List.of(FraudRuleCode.RAPID_TRANSACTION_COUNT, FraudRuleCode.WINDOW_AMOUNT_SUM)
                : List.of();
        String reason = buildReason(allEvaluations, windowResult);

        return new FraudRuleEngineResult(
                FraudRuleVersions.ACTIVE_RULE_VERSION,
                riskScore,
                riskLevel,
                decision,
                matchedRules,
                skippedRules,
                windowResult != null && windowResult.degraded(),
                reason
        );
    }

    private List<FraudRuleEvaluation> evaluateStatefulRules(RecentTransactionWindowResult windowResult) {
        if (windowResult == null || windowResult.degraded()) {
            return List.of();
        }

        boolean rapidCountMatched = windowResult.transactionCount() >= slidingWindowProperties.maxEvents();
        boolean amountSumMatched = windowResult.amountSum().compareTo(slidingWindowProperties.amountThreshold()) >= 0;

        return List.of(
                new FraudRuleEvaluation(
                        FraudRuleCode.RAPID_TRANSACTION_COUNT,
                        rapidCountMatched,
                        rapidCountMatched ? RAPID_TRANSACTION_COUNT_SCORE : 0,
                        "transaction count in sliding window >= " + slidingWindowProperties.maxEvents()
                ),
                new FraudRuleEvaluation(
                        FraudRuleCode.WINDOW_AMOUNT_SUM,
                        amountSumMatched,
                        amountSumMatched ? WINDOW_AMOUNT_SUM_SCORE : 0,
                        "amount sum in sliding window >= " + slidingWindowProperties.amountThreshold().toPlainString()
                )
        );
    }

    private String buildReason(
            List<FraudRuleEvaluation> evaluations,
            RecentTransactionWindowResult windowResult
    ) {
        String matchedReason = evaluations.stream()
                .filter(FraudRuleEvaluation::matched)
                .map(FraudRuleEvaluation::reason)
                .reduce((left, right) -> left + "; " + right)
                .orElse("No fraud rule matched");
        if (windowResult == null || !windowResult.degraded()) {
            return matchedReason;
        }
        return matchedReason + "; Redis degraded mode: " + windowResult.reason();
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
