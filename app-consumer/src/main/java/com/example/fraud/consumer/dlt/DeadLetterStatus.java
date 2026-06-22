package com.example.fraud.consumer.dlt;

public enum DeadLetterStatus {
    PENDING,
    REPROCESSING,
    REPROCESSED,
    DISCARDED,
    REPROCESS_FAILED
}
