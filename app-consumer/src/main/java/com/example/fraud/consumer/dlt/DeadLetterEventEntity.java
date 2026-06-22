package com.example.fraud.consumer.dlt;

import com.example.fraud.common.event.TransactionEventMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", nullable = false, length = 50)
    private FailureStage failureStage;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeadLetterEventEntity() {
    }

    private DeadLetterEventEntity(
            TransactionEventMessage message,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String dltTopic,
            FailureStage failureStage,
            String errorType,
            String errorMessage,
            String payloadJson,
            OffsetDateTime now
    ) {
        this.eventId = message.eventId();
        this.traceId = message.traceId();
        this.userId = message.userId();
        this.sourceTopic = sourceTopic;
        this.sourcePartition = sourcePartition;
        this.sourceOffset = sourceOffset;
        this.dltTopic = dltTopic;
        this.failureStage = failureStage;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.payloadJson = payloadJson;
        this.status = DeadLetterStatus.PENDING;
        this.reprocessAttempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static DeadLetterEventEntity pending(
            TransactionEventMessage message,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String dltTopic,
            FailureStage failureStage,
            String errorType,
            String errorMessage,
            String payloadJson,
            OffsetDateTime now
    ) {
        return new DeadLetterEventEntity(
                message,
                sourceTopic,
                sourcePartition,
                sourceOffset,
                dltTopic,
                failureStage,
                errorType,
                errorMessage,
                payloadJson,
                now
        );
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

    public FailureStage getFailureStage() {
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
}
