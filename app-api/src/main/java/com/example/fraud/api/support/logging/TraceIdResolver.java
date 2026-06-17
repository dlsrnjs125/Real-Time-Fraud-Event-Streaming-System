package com.example.fraud.api.support.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class TraceIdResolver {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return "trace-" + UUID.randomUUID();
        }
        return traceId;
    }
}
