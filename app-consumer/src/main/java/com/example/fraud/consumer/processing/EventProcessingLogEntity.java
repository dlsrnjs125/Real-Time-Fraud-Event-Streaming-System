package com.example.fraud.consumer.processing;

import com.example.fraud.common.event.TransactionEventMessage;
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

@Entity
@Table(
        name = "event_processing_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_processing_logs_topic_partition_offset",
                columnNames = {"topic", "partition_no", "offset_no"}
        )
)
public class EventProcessingLogEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "trace_id", nullable = false, length = 150)
    private String traceId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private int partitionNo;

    @Column(name = "offset_no", nullable = false)
    private long offsetNo;

    @Column(name = "consumer_group_id", nullable = false, length = 100)
    private String consumerGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventProcessingStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EventProcessingLogEntity() {
    }

    private EventProcessingLogEntity(
            TransactionEventMessage message,
            String topic,
            int partitionNo,
            long offsetNo,
            String consumerGroupId,
            EventProcessingStatus status,
            OffsetDateTime processedAt
    ) {
        this.eventId = message.eventId();
        this.traceId = message.traceId();
        this.userId = message.userId();
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.consumerGroupId = consumerGroupId;
        this.status = status;
        this.receivedAt = message.receivedAt();
        this.processedAt = processedAt;
    }

    public static EventProcessingLogEntity processed(
            TransactionEventMessage message,
            String topic,
            int partitionNo,
            long offsetNo,
            String consumerGroupId,
            OffsetDateTime processedAt
    ) {
        return new EventProcessingLogEntity(
                message,
                topic,
                partitionNo,
                offsetNo,
                consumerGroupId,
                EventProcessingStatus.PROCESSED,
                processedAt
        );
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

    public void markFailed(String errorMessage, OffsetDateTime processedAt) {
        this.status = EventProcessingStatus.FAILED;
        this.errorMessage = truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH);
        this.processedAt = processedAt;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    public String getTopic() {
        return topic;
    }

    public int getPartitionNo() {
        return partitionNo;
    }

    public long getOffsetNo() {
        return offsetNo;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public EventProcessingStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}
