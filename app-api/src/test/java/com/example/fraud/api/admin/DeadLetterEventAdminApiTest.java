package com.example.fraud.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fraud.api.admin.dlt.DeadLetterAdminMetrics;
import com.example.fraud.api.admin.dlt.DeadLetterPublishFailedException;
import com.example.fraud.api.admin.dlt.DeadLetterReprocessPublisher;
import io.micrometer.core.instrument.MeterRegistry;
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

    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private DeadLetterReprocessPublisher reprocessPublisher;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from admin_audit_logs");
        jdbcTemplate.update("delete from dead_letter_events");
    }

    @Test
    void listsDeadLetterEventsByStatus() throws Exception {
        insertDltEvent(1L, "evt-dlt-api-list-001", "PENDING");

        mockMvc.perform(get("/api/v1/admin/dlq-events")
                        .header("X-Admin-Token", ADMIN_TOKEN)
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

        mockMvc.perform(get("/api/v1/admin/dlq-events/{id}", 2L)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.eventId").value("evt-dlt-api-detail-001"))
                .andExpect(jsonPath("$.payloadJson").exists());
    }

    @Test
    void missingDeadLetterEventReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq-events/{id}", 404L)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DLQ_EVENT_NOT_FOUND"));
    }

    @Test
    void reprocessesPendingDeadLetterEvent() throws Exception {
        insertDltEvent(3L, "evt-dlt-api-reprocess-001", "PENDING");
        double before = metricCount(DeadLetterAdminMetrics.DLT_REPROCESS_REQUESTED_TOTAL, "success");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 3L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"manual replay after payload review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dlqId").value(3))
                .andExpect(jsonPath("$.status").value("REPROCESSED"))
                .andExpect(jsonPath("$.reprocessAttemptId").value("1"));

        assertAudit("DLT_REPROCESS", "SUCCESS", 3L, "operator-001");
        assertThat(metricCount(DeadLetterAdminMetrics.DLT_REPROCESS_REQUESTED_TOTAL, "success") - before)
                .isEqualTo(1.0);
    }

    @Test
    void reprocessWithoutRequestBodyReturnsBadRequest() throws Exception {
        insertDltEvent(11L, "evt-dlt-api-reprocess-validation-001", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 11L)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reprocessWithoutOperatorIdReturnsBadRequest() throws Exception {
        insertDltEvent(12L, "evt-dlt-api-reprocess-validation-002", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 12L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"manual replay after payload review"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }

    @Test
    void reprocessWithoutReasonReturnsBadRequest() throws Exception {
        insertDltEvent(13L, "evt-dlt-api-reprocess-validation-003", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 13L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }

    @Test
    void reprocessWithTooLongOperatorIdReturnsBadRequest() throws Exception {
        insertDltEvent(14L, "evt-dlt-api-reprocess-validation-004", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 14L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"%s","reason":"manual replay after payload review"}
                                """.formatted("a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }

    @Test
    void reprocessWithTooLongReasonReturnsBadRequest() throws Exception {
        insertDltEvent(15L, "evt-dlt-api-reprocess-validation-005", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 15L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"%s"}
                                """.formatted("a".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }

    @Test
    void publishFailureMarksReprocessFailed() throws Exception {
        insertDltEvent(4L, "evt-dlt-api-reprocess-fail-001", "PENDING");
        doThrow(new DeadLetterPublishFailedException(new RuntimeException("kafka unavailable")))
                .when(reprocessPublisher)
                .publish(any());

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 4L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"retry kafka publish"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KAFKA_PUBLISH_FAILED"));

        String status = jdbcTemplate.queryForObject(
                "select status from dead_letter_events where id = ?",
                String.class,
                4L
        );
        Integer attempts = jdbcTemplate.queryForObject(
                "select reprocess_attempts from dead_letter_events where id = ?",
                Integer.class,
                4L
        );
        assertThat(status).isEqualTo("REPROCESS_FAILED");
        assertThat(attempts).isEqualTo(1);
        assertAudit("DLT_REPROCESS", "FAILED", 4L, "operator-001");
    }

    @Test
    void discardsPendingDeadLetterEvent() throws Exception {
        insertDltEvent(5L, "evt-dlt-api-discard-001", "PENDING");
        double before = metricCount(DeadLetterAdminMetrics.DLT_DISCARDED_TOTAL, "success");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/discard", 5L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"invalid payload cannot be reprocessed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dlqId").value(5))
                .andExpect(jsonPath("$.status").value("DISCARDED"));

        assertAudit("DLT_DISCARD", "SUCCESS", 5L, "operator-001");
        assertThat(metricCount(DeadLetterAdminMetrics.DLT_DISCARDED_TOTAL, "success") - before)
                .isEqualTo(1.0);
    }

    @Test
    void discardWithTooLongReasonReturnsBadRequest() throws Exception {
        insertDltEvent(16L, "evt-dlt-api-discard-validation-001", "PENDING");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/discard", 16L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"%s"}
                                """.formatted("a".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }

    @Test
    void discardFailureWritesFailedAudit() throws Exception {
        insertDltEvent(8L, "evt-dlt-api-discard-fail-001", "REPROCESSED");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/discard", 8L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"late discard attempt"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DLT_STATE_CONFLICT"));

        assertAudit("DLT_DISCARD", "FAILED", 8L, "operator-001");
    }

    @Test
    void reprocessedEventCannotBeReprocessedAgain() throws Exception {
        insertDltEvent(6L, "evt-dlt-api-conflict-001", "REPROCESSED");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 6L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"retry already processed event"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DLT_STATE_CONFLICT"));
    }

    @Test
    void discardedEventCannotBeReprocessed() throws Exception {
        insertDltEvent(7L, "evt-dlt-api-conflict-002", "DISCARDED");

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 7L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"retry discarded event"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DLT_STATE_CONFLICT"));
    }

    @Test
    void maxAttemptsExceededReturnsConflictAndDoesNotPublish() throws Exception {
        insertDltEvent(9L, "evt-dlt-api-max-attempts-001", "REPROCESS_FAILED", 3);

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 9L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"manual replay after repeated failures"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_REPROCESS_ATTEMPTS_EXCEEDED"));

        verify(reprocessPublisher, never()).publish(any());
        assertAudit("DLT_REPROCESS", "FAILED", 9L, "operator-001");
    }

    @Test
    void attemptsBelowMaxCanBeReprocessed() throws Exception {
        insertDltEvent(10L, "evt-dlt-api-max-attempts-002", "REPROCESS_FAILED", 2);

        mockMvc.perform(post("/api/v1/admin/dlq-events/{id}/reprocess", 10L)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"operator-001","reason":"third and final allowed attempt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessAttemptId").value("3"));
    }

    private void insertDltEvent(long id, String eventId, String status) {
        insertDltEvent(id, eventId, status, 0);
    }

    private void insertDltEvent(long id, String eventId, String status, int attempts) {
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
                attempts,
                now,
                now
        );
    }

    private void assertAudit(String action, String result, long dlqId, String actor) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from admin_audit_logs
                        where action = ? and result = ? and target_id = ? and actor = ?
                        """,
                Integer.class,
                action,
                result,
                String.valueOf(dlqId),
                actor
        );
        assertThat(count).isEqualTo(1);

        String metadata = jdbcTemplate.queryForObject(
                "select metadata_json from admin_audit_logs where action = ? and target_id = ?",
                String.class,
                action,
                String.valueOf(dlqId)
        );
        assertThat(metadata).doesNotContain("payload_json", "accountId", "deviceId", "test-admin-token");

        String requestId = jdbcTemplate.queryForObject(
                "select request_id from admin_audit_logs where action = ? and target_id = ?",
                String.class,
                action,
                String.valueOf(dlqId)
        );
        assertThat(requestId).isNull();
    }

    private double metricCount(String metricName, String result) {
        return meterRegistry.counter(metricName, "result", result).count();
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
