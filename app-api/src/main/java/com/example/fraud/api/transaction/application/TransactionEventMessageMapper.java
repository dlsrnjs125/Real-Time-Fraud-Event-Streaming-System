package com.example.fraud.api.transaction.application;

import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptEntity;
import com.example.fraud.common.event.TransactionEventMessage;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventMessageMapper {

    public TransactionEventMessage toMessage(TransactionEventReceiptEntity receipt) {
        return new TransactionEventMessage(
                receipt.getSchemaVersion(),
                receipt.getEventId(),
                receipt.getUserId(),
                receipt.getAccountId(),
                receipt.getEventType(),
                receipt.getAmount(),
                receipt.getCurrency(),
                receipt.getMerchantId(),
                receipt.getDeviceId(),
                receipt.getLocation(),
                receipt.getEventTime(),
                receipt.getReceivedAt(),
                receipt.getTraceId()
        );
    }

    public TransactionEventReceiptEntity toReceipt(
            TransactionEventRequest request,
            String traceId,
            java.time.OffsetDateTime receivedAt
    ) {
        return new TransactionEventReceiptEntity(
                request.eventId(),
                TransactionEventMessage.CURRENT_SCHEMA_VERSION,
                request.userId(),
                request.accountId(),
                request.eventType(),
                request.amount(),
                request.currency(),
                request.merchantId(),
                request.deviceId(),
                request.location(),
                request.eventTime(),
                receivedAt,
                traceId
        );
    }
}
