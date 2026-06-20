package com.example.fraud.api.admin.fraud;

import com.example.fraud.common.event.FraudDecision;
import com.example.fraud.common.event.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fraud_detection_results")
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

    @Column(name = "skipped_rules")
    private String skippedRules;

    @Column(name = "degraded", nullable = false)
    private boolean degraded;

    @Column(name = "reason")
    private String reason;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    protected FraudDetectionResultEntity() {
    }

    public String getEventId() {
        return eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccountId() {
        return accountId;
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

    public String getMatchedRules() {
        return matchedRules;
    }

    public String getSkippedRules() {
        return skippedRules;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }
}
