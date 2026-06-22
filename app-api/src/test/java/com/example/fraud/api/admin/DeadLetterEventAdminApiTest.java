package com.example.fraud.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fraud.api.admin.dlt.DeadLetterReprocessPublisher;
import com.example.fraud.api.admin.dlt.DeadLetterPublishFailedException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeadLetterEventAdminApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DeadLetterReprocessPublisher reprocessPublisher;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from dead_letter_events");
    }

    @Test
    void listsDeadLetterEventsByStatus() throws Exception {
        insertDltEvent(1L, "evt-dlt-api-list-001", "PENDING");

        mockMvc.perform(get("/api/v1/admin/dlq-events")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].dlqId").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("evt-dlt-api-list-001"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void getsDeadLetterEventDetail() throws Exception {
        insertDltEvent(2L, "evt-dlt-api-detail-001", "PENDING");

        mockMvc.perform(get("/api/v1/admin/dlq-events/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.eventId").value("evt-dlt-api-detail-001"))
                .andExpect(jsonPath("$.payloadJson").exists());
    }

    @Test
    void missingDeadLetterEventReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq-events/{id}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DLQ_EVENT_NOT_FOUND"));
    }

    @Test
    void reprocessesPendingDeadLetterEvent() throws Exception {
        insertDltEvent(3L, "evt-dlt-api-reprocess-001", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dlqId").value(3))
                .andExpect(jsonPath("$.status").value("REPROCESSED"))
                .andExpect(jsonPath("$.reprocessAttemptId").value("1"));
    }

    @Test
    void publishFailureMarksReprocessFailed() throws Exception {
        insertDltEvent(4L, "evt-dlt-api-reprocess-fail-001", "PENDING");
        doThrow(new DeadLetterPublishFailedException(new RuntimeException("kafka unavailable")))
                .when(reprocessPublisher)
                .publish(any());

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 4L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROCESS_FAILED"))
                .andExpect(jsonPath("$.reprocessAttemptId").value("1"));
    }

    @Test
    void discardsPendingDeadLetterEvent() throws Exception {
        insertDltEvent(5L, "evt-dlt-api-discard-001", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/discard", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"invalid payload cannot be reprocessed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dlqId").value(5))
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    @Test
    void reprocessedEventCannotBeReprocessedAgain() throws Exception {
        insertDltEvent(6L, "evt-dlt-api-conflict-001", "REPROCESSED");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 6L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DLT_STATE_CONFLICT"));
    }

    @Test
    void discardedEventCannotBeReprocessed() throws Exception {
        insertDltEvent(7L, "evt-dlt-api-conflict-002", "DISCARDED");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DLT_STATE_CONFLICT"));
    }

    private void insertDltEvent(long id, String eventId, String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-22T10:00:00Z");
        jdbcTemplate.update("""
                        insert into dead_letter_events (
                            id,
                            event_id,
                            trace_id,
                            user_id,
                            source_topic,
                            source_partition,
                            source_offset,
                            dlt_topic,
                            failure_stage,
                            error_type,
                            error_message,
                            payload_json,
                            status,
                            reprocess_attempts,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                eventId,
                "trace-" + eventId,
                "user-1001",
                "transaction-events",
                0,
                id,
                "transaction-events-dlt",
                "RULE_ENGINE_ERROR",
                "RuntimeException",
                "rule failed",
                payloadJson(eventId),
                status,
                0,
                now,
                now
        );
    }

    private String payloadJson(String eventId) {
        return """
                {
                  "schemaVersion": "v1",
                  "eventId": "%s",
                  "userId": "user-1001",
                  "accountId": "acc-1001",
                  "eventType": "PAYMENT",
                  "amount": 120000,
                  "currency": "KRW",
                  "merchantId": "merchant-001",
                  "deviceId": "device-001",
                  "location": "SEOUL",
                  "eventTime": "2026-06-22T10:00:00Z",
                  "receivedAt": "2026-06-22T10:00:01Z",
                  "traceId": "trace-%s"
                }
                """.formatted(eventId, eventId);
    }
}
