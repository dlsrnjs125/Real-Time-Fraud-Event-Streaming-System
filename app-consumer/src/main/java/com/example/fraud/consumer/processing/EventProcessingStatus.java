package com.example.fraud.consumer.processing;

public enum EventProcessingStatus {
    CONSUMED,
    PROCESSED,
    FAILED,
    DUPLICATE_SKIPPED
}
