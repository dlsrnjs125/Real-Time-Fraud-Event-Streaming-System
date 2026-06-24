package com.example.fraud.api.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditLogService {

    private static final String TARGET_TYPE_DLT_EVENT = "DLT_EVENT";

    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuditLogService(AdminAuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void recordDltAction(
            String actor,
            AdminAction action,
            long dlqId,
            String eventId,
            String traceId,
            AdminAuditResult result,
            String reason,
            Map<String, Object> metadata
    ) {
        repository.save(new AdminAuditLogEntity(
                normalizeActor(actor),
                action,
                TARGET_TYPE_DLT_EVENT,
                String.valueOf(dlqId),
                eventId,
                traceId,
                result,
                limit(reason, 500),
                toMetadataJson(metadata),
                OffsetDateTime.now(clock)
        ));
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "admin-api";
        }
        return limit(actor, 100);
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize admin audit metadata", exception);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
