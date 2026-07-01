package com.example.fraud.api.admin.dlt;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterAdminMetrics {

    public static final String DLT_REPROCESS_REQUESTED_TOTAL = "fraud.dlt.reprocess.requested.total";
    public static final String DLT_DISCARDED_TOTAL = "fraud.dlt.discarded.total";

    private final MeterRegistry meterRegistry;

    public DeadLetterAdminMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementReprocessRequested(String result) {
        meterRegistry.counter(DLT_REPROCESS_REQUESTED_TOTAL, "result", result).increment();
    }

    public void incrementDiscarded(String result) {
        meterRegistry.counter(DLT_DISCARDED_TOTAL, "result", result).increment();
    }
}
