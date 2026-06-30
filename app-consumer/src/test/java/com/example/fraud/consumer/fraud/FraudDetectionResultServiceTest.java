package com.example.fraud.consumer.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.example.fraud.consumer.rule.FraudRuleEngineResult;
import com.example.fraud.consumer.rule.FraudRuleVersions;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(FraudDetectionResultServiceTest.TestConfig.class)
class FraudDetectionResultServiceTest {

    @Autowired
    private FraudDetectionResultRepository repository;

    @Autowired
    private FraudDetectionResultService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void savesFraudDetectionResult() {
        FraudDetectionResultSaveResult result = service.saveResult(message("evt-fraud-result-001"), highRiskResult());

        assertThat(result.duplicateSkipped()).isFalse();
        var saved = repository.findByEventId("evt-fraud-result-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getRiskScore()).isEqualTo(80);
        assertThat(saved.get().getRuleVersion()).isEqualTo(FraudRuleVersions.ACTIVE_RULE_VERSION);
        assertThat(saved.get().getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(saved.get().getDecision()).isEqualTo(FraudDecision.BLOCK);
    }

    @Test
    void duplicateEventIdDoesNotCreateAnotherResult() {
        TransactionEventMessage message = message("evt-fraud-result-duplicate");

        FraudDetectionResultSaveResult first = service.saveResult(message, highRiskResult());
        FraudDetectionResultSaveResult second = service.saveResult(message, highRiskResult());

        assertThat(first.duplicateSkipped()).isFalse();
        assertThat(second.duplicateSkipped()).isTrue();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateEventIdDirectly() {
        insertFraudResult("evt-fraud-result-constraint", "trace-1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        insertFraudResult("evt-fraud-result-constraint", "trace-2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRiskScoreOutsideRange() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into fraud_detection_results (
                            event_id,
                            trace_id,
                            user_id,
                            account_id,
                            risk_score,
                            risk_level,
                            decision,
                            matched_rules,
                            reason,
                            detected_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "evt-fraud-result-invalid-score",
                "trace-invalid-score",
                "user-1001",
                "acc-1001",
                101,
                "HIGH",
                "BLOCK",
                "AMOUNT_THRESHOLD",
                "invalid score",
                OffsetDateTime.parse("2026-06-19T10:00:02Z"),
                OffsetDateTime.parse("2026-06-19T10:00:02Z"),
                OffsetDateTime.parse("2026-06-19T10:00:02Z")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertFraudResult(String eventId, String traceId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-19T10:00:02Z");
        jdbcTemplate.update("""
                        insert into fraud_detection_results (
                            event_id,
                            trace_id,
                            user_id,
                            account_id,
                            risk_score,
                            risk_level,
                            decision,
                            matched_rules,
                            reason,
                            detected_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                traceId,
                "user-1001",
                "acc-1001",
                80,
                "HIGH",
                "BLOCK",
                "AMOUNT_THRESHOLD,SUSPICIOUS_LOCATION",
                "test reason",
                now,
                now,
                now
        );
    }

    private TransactionEventMessage message(String eventId) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-19T10:00:00Z");
        return new TransactionEventMessage(
                "v1",
                eventId,
                "user-1001",
                "acc-1001",
                TransactionEventType.PAYMENT,
                BigDecimal.valueOf(1_500_000),
                "KRW",
                "merchant-001",
                "device-001",
                "HIGH_RISK",
                eventTime,
                eventTime.plusSeconds(1),
                "trace-" + eventId
        );
    }

    private FraudRuleEngineResult highRiskResult() {
        return new FraudRuleEngineResult(
                FraudRuleVersions.ACTIVE_RULE_VERSION,
                80,
                RiskLevel.HIGH,
                FraudDecision.BLOCK,
                List.of(FraudRuleCode.AMOUNT_THRESHOLD, FraudRuleCode.SUSPICIOUS_LOCATION),
                List.of(),
                false,
                "amount >= 1000000 KRW; location is UNKNOWN, FOREIGN, or HIGH_RISK"
        );
    }

    static class TestConfig {

        @org.springframework.context.annotation.Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-19T10:00:02Z"), ZoneOffset.UTC);
        }

        @org.springframework.context.annotation.Bean
        FraudDetectionResultService fraudDetectionResultService(
                FraudDetectionResultRepository repository,
                Clock clock
        ) {
            return new FraudDetectionResultService(repository, clock);
        }
    }
}
