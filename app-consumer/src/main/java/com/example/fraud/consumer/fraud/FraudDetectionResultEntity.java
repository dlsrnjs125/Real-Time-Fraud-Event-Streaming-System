package com.example.fraud.consumer.fraud;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.rule.FraudRuleEngineResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(
        name = "fraud_detection_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fraud_detection_results_event_id",
                columnNames = "event_id"
        )
)
public class FraudDetectionResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "trace_id", nullable = false, length = 150)
    private String traceId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 30)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private FraudDecision decision;

    @Column(name = "matched_rules")
    private String matchedRules;

    @Column(name = "reason")
    private String reason;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FraudDetectionResultEntity() {
    }

    private FraudDetectionResultEntity(
            TransactionEventMessage message,
            FraudRuleEngineResult ruleResult,
            OffsetDateTime detectedAt
    ) {
        this.eventId = message.eventId();
        this.traceId = message.traceId();
        this.userId = message.userId();
        this.accountId = message.accountId();
        this.riskScore = ruleResult.riskScore();
        this.riskLevel = ruleResult.riskLevel();
        this.decision = ruleResult.decision();
        this.matchedRules = serialize(ruleResult.matchedRules());
        this.reason = ruleResult.reason();
        this.detectedAt = detectedAt;
    }

    public static FraudDetectionResultEntity from(
            TransactionEventMessage message,
            FraudRuleEngineResult ruleResult,
            OffsetDateTime detectedAt
    ) {
        return new FraudDetectionResultEntity(message, ruleResult, detectedAt);
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    private String serialize(List<FraudRuleCode> matchedRules) {
        return String.join(",", matchedRules.stream().map(Enum::name).toList());
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public FraudDecision getDecision() {
        return decision;
    }
}
