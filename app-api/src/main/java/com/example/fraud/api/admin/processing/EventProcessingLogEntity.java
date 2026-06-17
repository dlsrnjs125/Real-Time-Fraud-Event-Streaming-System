package com.example.fraud.api.admin.processing;

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
@Table(name = "event_processing_logs")
public class EventProcessingLogEntity {

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
    private ProcessingLogStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected EventProcessingLogEntity() {
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

    public ProcessingLogStatus getStatus() {
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
