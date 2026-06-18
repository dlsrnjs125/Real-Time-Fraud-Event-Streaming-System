package com.example.fraud.consumer.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FraudRuleEngineTest {

    private final FraudRuleEngine ruleEngine = new FraudRuleEngine(List.of(
            new AmountThresholdRule(),
            new NightTimeTransactionRule(),
            new SuspiciousLocationRule()
    ));

    @Test
    void evaluatesLowRiskTransaction() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(10_000),
                "SEOUL",
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.riskScore()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVE);
        assertThat(result.matchedRules()).isEmpty();
    }

    @Test
    void evaluatesHighAmountTransaction() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(1_000_000),
                "SEOUL",
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(50);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(result.matchedRules()).containsExactly(FraudRuleCode.AMOUNT_THRESHOLD);
    }

    @Test
    void evaluatesNightTimeTransaction() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(10_000),
                "SEOUL",
                "2026-06-19T02:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(20);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVE);
        assertThat(result.matchedRules()).containsExactly(FraudRuleCode.NIGHT_TIME_TRANSACTION);
    }

    @Test
    void evaluatesSuspiciousLocationTransaction() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(10_000),
                "HIGH_RISK",
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(30);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(result.matchedRules()).containsExactly(FraudRuleCode.SUSPICIOUS_LOCATION);
    }

    @Test
    void sumsMultipleMatchedRules() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(1_500_000),
                "HIGH_RISK",
                "2026-06-19T02:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(100);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(result.matchedRules()).containsExactly(
                FraudRuleCode.AMOUNT_THRESHOLD,
                FraudRuleCode.NIGHT_TIME_TRANSACTION,
                FraudRuleCode.SUSPICIOUS_LOCATION
        );
    }

    @Test
    void capsRiskScoreAt100() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(1_500_000),
                "FOREIGN",
                "2026-06-19T02:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void mapsHighRiskToBlockDecision() {
        FraudRuleEngineResult result = ruleEngine.evaluate(message(
                BigDecimal.valueOf(1_500_000),
                "HIGH_RISK",
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.riskScore()).isEqualTo(80);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
    }

    private TransactionEventMessage message(BigDecimal amount, String location, String eventTime) {
        OffsetDateTime time = OffsetDateTime.parse(eventTime);
        return new TransactionEventMessage(
                "v1",
                "evt-rule-test",
                "user-1001",
                "acc-1001",
                TransactionEventType.PAYMENT,
                amount,
                "KRW",
                "merchant-001",
                "device-001",
                location,
                time,
                time.plusSeconds(1),
                "trace-rule-test"
        );
    }
}
