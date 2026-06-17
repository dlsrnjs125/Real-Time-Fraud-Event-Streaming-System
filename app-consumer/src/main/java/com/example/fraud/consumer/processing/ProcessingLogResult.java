package com.example.fraud.consumer.processing;

public record ProcessingLogResult(boolean duplicateSkipped) {

    public static ProcessingLogResult processed() {
        return new ProcessingLogResult(false);
    }

    public static ProcessingLogResult duplicate() {
        return new ProcessingLogResult(true);
    }
}
