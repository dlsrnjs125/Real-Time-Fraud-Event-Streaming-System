package com.example.fraud.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FraudDetectionResultQueryApiTest {

    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from fraud_detection_results");
    }

    @Test
    void returnsFraudDetectionResultForEventId() throws Exception {
        insertFraudResult(
                "evt-fraud-query-001",
                "trace-fraud-query-001",
                "AMOUNT_THRESHOLD,NIGHT_TIME_TRANSACTION",
                70,
                "HIGH",
                "BLOCK"
        );

        mockMvc.perform(get("/api/v1/admin/events/{eventId}/fraud-result", "evt-fraud-query-001")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-fraud-query-001"))
                .andExpect(jsonPath("$.traceId").value("trace-fraud-query-001"))
                .andExpect(jsonPath("$.userId").value("user-1001"))
                .andExpect(jsonPath("$.accountId").value("acc-1001"))
                .andExpect(jsonPath("$.riskScore").value(70))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.matchedRules[0]").value("AMOUNT_THRESHOLD"))
                .andExpect(jsonPath("$.matchedRules[1]").value("NIGHT_TIME_TRANSACTION"))
                .andExpect(jsonPath("$.skippedRules").isEmpty())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.reason").value("high amount during night time"));
    }

    @Test
    void missingFraudDetectionResultReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events/{eventId}/fraud-result", "evt-missing-fraud-result")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRAUD_RESULT_NOT_FOUND"));
    }

    private void insertFraudResult(
            String eventId,
            String traceId,
            String matchedRules,
            int riskScore,
            String riskLevel,
            String decision
    ) {
        OffsetDateTime detectedAt = OffsetDateTime.parse("2026-06-19T10:00:02Z");
        jdbcTemplate.update("""
                        insert into fraud_detection_results (
                            event_id,
                            trace_id,
                            user_id,
                            account_id,
                            risk_score,
                            risk_level,
                            decision,
                            matched_rules,
                            reason,
                            detected_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                eventId,
                traceId,
                "user-1001",
                "acc-1001",
                riskScore,
                riskLevel,
                decision,
                matchedRules,
                "high amount during night time",
                detectedAt,
                detectedAt,
                detectedAt
        );
    }
}
