package com.example.fraud.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminApiWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ADMIN_API"));
    }

    @Test
    void adminApiWithInvalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq-events")
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ADMIN_API"));
    }

    @Test
    void adminApiWithValidTokenCanProceed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq-events")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void transactionIngestApiDoesNotRequireAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-1001",
                                  "accountId": "acc-3001",
                                  "eventType": "PAYMENT",
                                  "amount": 1500000,
                                  "currency": "KRW",
                                  "eventTime": "2026-06-15T10:30:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"));
    }
}
