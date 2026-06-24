package com.example.fraud.api.admin.audit;

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
@Table(name = "admin_audit_logs")
public class AdminAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100)
    private AdminAction action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private AdminAuditResult result;

    @Column(name = "reason")
    private String reason;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AdminAuditLogEntity() {
    }

    public AdminAuditLogEntity(
            String actor,
            AdminAction action,
            String targetType,
            String targetId,
            String requestId,
            String traceId,
            AdminAuditResult result,
            String reason,
            String metadataJson,
            OffsetDateTime createdAt
    ) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.traceId = traceId;
        this.result = result;
        this.reason = reason;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public AdminAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public AdminAuditResult getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
