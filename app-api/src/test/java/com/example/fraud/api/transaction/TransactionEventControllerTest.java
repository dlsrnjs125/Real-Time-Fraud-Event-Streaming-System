package com.example.fraud.api.transaction;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fraud.api.transaction.application.KafkaPublishFailedException;
import com.example.fraud.api.transaction.kafka.TransactionEventProducer;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptRepository;
import com.example.fraud.api.transaction.persistence.TransactionEventReceiptStatus;
import com.example.fraud.common.event.TransactionEventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionEventReceiptRepository receiptRepository;

    @MockBean
    private TransactionEventProducer producer;

    @AfterEach
    void tearDown() {
        receiptRepository.deleteAll();
    }

    @Test
    void invalidAmountReturnsErrorResponseWithTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-invalid-amount",
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
    void validRequestSavesReceiptAndPublishesTransactionEvent() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-test-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("evt-phase3-001", "user-1001")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-phase3-001"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.traceId").value("trace-test-003"));

        var receipt = receiptRepository.findByEventId("evt-phase3-001").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(receipt.getStatus()).isEqualTo(TransactionEventReceiptStatus.PUBLISHED);

        ArgumentCaptor<TransactionEventMessage> messageCaptor = ArgumentCaptor.forClass(TransactionEventMessage.class);
        verify(producer).publish(messageCaptor.capture());
        TransactionEventMessage message = messageCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(message.schemaVersion()).isEqualTo("v1");
        org.assertj.core.api.Assertions.assertThat(message.eventId()).isEqualTo("evt-phase3-001");
        org.assertj.core.api.Assertions.assertThat(message.userId()).isEqualTo("user-1001");
        org.assertj.core.api.Assertions.assertThat(message.receivedAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(message.traceId()).isEqualTo("trace-test-003");
    }

    @Test
    void duplicateEventIdReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-duplicate-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("evt-duplicate-001", "user-1001")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-duplicate-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("evt-duplicate-001", "user-1001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION_EVENT"))
                .andExpect(jsonPath("$.traceId").value("trace-duplicate-002"));
    }

    @Test
    void kafkaPublishFailureReturnsServiceUnavailableAndMarksReceiptFailed() throws Exception {
        doThrow(new KafkaPublishFailedException(new RuntimeException("broker unavailable")))
                .when(producer).publish(any(TransactionEventMessage.class));

        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-publish-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("evt-publish-fail", "user-1001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KAFKA_PUBLISH_FAILED"))
                .andExpect(jsonPath("$.message").value("failed to publish transaction event"))
                .andExpect(jsonPath("$.traceId").value("trace-publish-fail"));

        var receipt = receiptRepository.findByEventId("evt-publish-fail").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(receipt.getStatus()).isEqualTo(TransactionEventReceiptStatus.PUBLISH_FAILED);
    }

    @Test
    void receiptCanBeRetrievedAfterSuccessfulIntake() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-get-receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("evt-get-receipt", "user-2001")))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/transactions/events/{eventId}", "evt-get-receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-get-receipt"))
                .andExpect(jsonPath("$.userId").value("user-2001"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.traceId").value("trace-get-receipt"));
    }

    @Test
    void errorResponseDoesNotExposeRawPayload() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/events")
                        .header("X-Trace-Id", "trace-raw-payload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-raw-payload",
                                  "userId": "user-1001",
                                  "accountId": "acc-sensitive-raw",
                                  "eventType": "PAYMENT",
                                  "amount": 1500000,
                                  "currency": "USD",
                                  "eventTime": "2026-06-15T10:30:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_EVENT"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(containsString("acc-sensitive-raw"))))
                .andExpect(jsonPath("$.traceId").value("trace-raw-payload"));
    }

    private String validRequest(String eventId, String userId) {
        return """
                {
                  "eventId": "%s",
                  "userId": "%s",
                  "accountId": "acc-3001",
                  "eventType": "PAYMENT",
                  "amount": 1500000,
                  "currency": "KRW",
                  "merchantId": "merchant-777",
                  "deviceId": "device-abc",
                  "location": "KR",
                  "eventTime": "2026-06-15T10:30:00+09:00"
                }
                """.formatted(eventId, userId);
    }
}
