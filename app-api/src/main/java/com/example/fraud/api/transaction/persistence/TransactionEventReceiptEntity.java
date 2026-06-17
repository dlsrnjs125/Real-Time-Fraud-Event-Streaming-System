package com.example.fraud.api.transaction.persistence;

import com.example.fraud.common.event.TransactionEventType;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "transaction_event_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_event_receipts_event_id", columnNames = "event_id")
)
public class TransactionEventReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TransactionEventType eventType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "trace_id", nullable = false, length = 150)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionEventReceiptStatus status;

    @Column(name = "publish_error_message", length = 500)
    private String publishErrorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TransactionEventReceiptEntity() {
    }

    public TransactionEventReceiptEntity(
            String eventId,
            String schemaVersion,
            String userId,
            String accountId,
            TransactionEventType eventType,
            BigDecimal amount,
            String currency,
            String merchantId,
            String deviceId,
            String location,
            OffsetDateTime eventTime,
            OffsetDateTime receivedAt,
            String traceId
    ) {
        this.eventId = eventId;
        this.schemaVersion = schemaVersion;
        this.userId = userId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.deviceId = deviceId;
        this.location = location;
        this.eventTime = eventTime;
        this.receivedAt = receivedAt;
        this.traceId = traceId;
        this.status = TransactionEventReceiptStatus.RECEIVED;
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

    public void markPublished() {
        this.status = TransactionEventReceiptStatus.PUBLISHED;
        this.publishErrorMessage = null;
    }

    public void markPublishFailed(String message) {
        this.status = TransactionEventReceiptStatus.PUBLISH_FAILED;
        this.publishErrorMessage = message;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionEventType getEventType() {
        return eventType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getLocation() {
        return location;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public TransactionEventReceiptStatus getStatus() {
        return status;
    }

    public String getPublishErrorMessage() {
        return publishErrorMessage;
    }
}
