package com.example.fraud.api.support.exception;

public enum ApiErrorCode {
    INVALID_TRANSACTION_EVENT,
    EVENT_NOT_FOUND,
    FRAUD_RESULT_NOT_FOUND,
    DLQ_EVENT_NOT_FOUND,
    DLQ_EVENT_NOT_REPROCESSABLE,
    KAFKA_PUBLISH_FAILED,
    INTERNAL_ERROR
}
