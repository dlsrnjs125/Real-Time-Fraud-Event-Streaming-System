package com.example.fraud.api.admin.dlt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "dead_letter_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dead_letter_events_source_offset",
                columnNames = {"source_topic", "source_partition", "source_offset"}
        )
)
public class DeadLetterEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "trace_id", nullable = false, length = 100)
    private String traceId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "source_topic", nullable = false, length = 100)
    private String sourceTopic;

    @Column(name = "source_partition", nullable = false)
    private int sourcePartition;

    @Column(name = "source_offset", nullable = false)
    private long sourceOffset;

    @Column(name = "dlt_topic", nullable = false, length = 100)
    private String dltTopic;

    @Column(name = "failure_stage", nullable = false, length = 50)
    private String failureStage;

    @Column(name = "error_type", nullable = false, length = 200)
    private String errorType;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeadLetterStatus status;

    @Column(name = "reprocess_attempts", nullable = false)
    private int reprocessAttempts;

    @Column(name = "last_reprocessed_at")
    private OffsetDateTime lastReprocessedAt;

    @Column(name = "discarded_at")
    private OffsetDateTime discardedAt;

    @Column(name = "discard_reason")
    private String discardReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeadLetterEventEntity() {
    }

    public void startReprocessing(OffsetDateTime now) {
        if (!canRetry()) {
            throw new DeadLetterStateConflictException(id, status);
        }
        status = DeadLetterStatus.REPROCESSING;
        reprocessAttempts++;
        lastReprocessedAt = now;
        updatedAt = now;
    }

    public void markReprocessed(OffsetDateTime now) {
        if (status != DeadLetterStatus.REPROCESSING) {
            throw new DeadLetterStateConflictException(id, status);
        }
        status = DeadLetterStatus.REPROCESSED;
        updatedAt = now;
    }

    public void markReprocessFailed(OffsetDateTime now) {
        if (status != DeadLetterStatus.REPROCESSING) {
            throw new DeadLetterStateConflictException(id, status);
        }
        status = DeadLetterStatus.REPROCESS_FAILED;
        updatedAt = now;
    }

    public void discard(String reason, OffsetDateTime now) {
        if (!canRetry()) {
            throw new DeadLetterStateConflictException(id, status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("discard reason is required");
        }
        status = DeadLetterStatus.DISCARDED;
        discardReason = reason;
        discardedAt = now;
        updatedAt = now;
    }

    private boolean canRetry() {
        return status == DeadLetterStatus.PENDING || status == DeadLetterStatus.REPROCESS_FAILED;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
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

    public String getSourceTopic() {
        return sourceTopic;
    }

    public int getSourcePartition() {
        return sourcePartition;
    }

    public long getSourceOffset() {
        return sourceOffset;
    }

    public String getDltTopic() {
        return dltTopic;
    }

    public String getFailureStage() {
        return failureStage;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public DeadLetterStatus getStatus() {
        return status;
    }

    public int getReprocessAttempts() {
        return reprocessAttempts;
    }

    public OffsetDateTime getLastReprocessedAt() {
        return lastReprocessedAt;
    }

    public OffsetDateTime getDiscardedAt() {
        return discardedAt;
    }

    public String getDiscardReason() {
        return discardReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
