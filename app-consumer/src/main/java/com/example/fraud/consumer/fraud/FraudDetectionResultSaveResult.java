package com.example.fraud.consumer.fraud;

public record FraudDetectionResultSaveResult(
        boolean duplicateSkipped
) {
    public static FraudDetectionResultSaveResult saved() {
        return new FraudDetectionResultSaveResult(false);
    }

    public static FraudDetectionResultSaveResult duplicate() {
        return new FraudDetectionResultSaveResult(true);
    }
}
