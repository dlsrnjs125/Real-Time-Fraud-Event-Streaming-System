package com.example.fraud.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;

@SpringBootTest
@AutoConfigureMockMvc
class ProcessingLogQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from event_processing_logs");
    }

    @Test
    void returnsProcessingLogsForEventId() throws Exception {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-06-17T10:00:01Z");
        OffsetDateTime processedAt = OffsetDateTime.parse("2026-06-17T10:00:02Z");

        jdbcTemplate.update("""
                        insert into event_processing_logs (
                            event_id,
                            trace_id,
                            user_id,
                            topic,
                            partition_no,
                            offset_no,
                            consumer_group_id,
                            status,
                            error_message,
                            received_at,
                            processed_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "evt-processing-query-001",
                "trace-processing-query-001",
                "user-1001",
                "transaction-events",
                2,
                1532L,
                "fraud-event-consumer",
                "PROCESSED",
                null,
                receivedAt,
                processedAt,
                processedAt,
                processedAt
        );

        mockMvc.perform(get("/api/v1/admin/events/{eventId}/processing-log", "evt-processing-query-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-processing-query-001"))
                .andExpect(jsonPath("$.logs[0].eventId").value("evt-processing-query-001"))
                .andExpect(jsonPath("$.logs[0].traceId").value("trace-processing-query-001"))
                .andExpect(jsonPath("$.logs[0].userId").value("user-1001"))
                .andExpect(jsonPath("$.logs[0].topic").value("transaction-events"))
                .andExpect(jsonPath("$.logs[0].partition").value(2))
                .andExpect(jsonPath("$.logs[0].offset").value(1532))
                .andExpect(jsonPath("$.logs[0].consumerGroupId").value("fraud-event-consumer"))
                .andExpect(jsonPath("$.logs[0].status").value("PROCESSED"));
    }

    @Test
    void missingEventIdReturnsEmptyLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events/{eventId}/processing-log", "evt-missing-processing-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-missing-processing-log"))
                .andExpect(jsonPath("$.logs").isArray())
                .andExpect(jsonPath("$.logs").isEmpty());
    }
}
