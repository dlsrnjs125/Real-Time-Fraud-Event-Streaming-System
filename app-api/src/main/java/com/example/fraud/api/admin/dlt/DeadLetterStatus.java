package com.example.fraud.api.admin.dlt;

public enum DeadLetterStatus {
    PENDING,
    REPROCESSING,
    REPROCESSED,
    DISCARDED,
    REPROCESS_FAILED
}
