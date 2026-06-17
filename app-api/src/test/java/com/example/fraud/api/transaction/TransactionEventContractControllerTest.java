package com.example.fraud.api.transaction;

import static org.hamcrest.Matchers.containsString;
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
class TransactionEventContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidAmountReturnsErrorResponseWithTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-20260615-000001",
                                  "userId": "user-1001",
                                  "accountId": "acc-3001",
                                  "eventType": "PAYMENT",
                                  "amount": 0,
                                  "currency": "KRW",
                                  "eventTime": "2026-06-15T10:30:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"))
                .andExpect(jsonPath("$.message", containsString("amount")))
                .andExpect(jsonPath("$.traceId").value("trace-test-001"));
    }

    @Test
    void missingEventIdReturnsErrorResponseWithTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-test-002")
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
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"))
                .andExpect(jsonPath("$.message", containsString("eventId")))
                .andExpect(jsonPath("$.traceId").value("trace-test-002"));
    }

    @Test
    void validContractRequestReturnsAcceptedSkeletonResponse() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-test-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-20260615-000001",
                                  "userId": "user-1001",
                                  "accountId": "acc-3001",
                                  "eventType": "PAYMENT",
                                  "amount": 1500000,
                                  "currency": "KRW",
                                  "merchantId": "merchant-777",
                                  "deviceId": "device-abc",
                                  "location": "KR",
                                  "eventTime": "2026-06-15T10:30:00+09:00"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-20260615-000001"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.traceId").value("trace-test-003"));
    }
}
