package com.example.fraud.api.transaction;

import com.example.fraud.api.support.exception.ErrorResponse;
import com.example.fraud.api.support.logging.TraceIdResolver;
import com.example.fraud.api.transaction.dto.TransactionEventAcceptedResponse;
import com.example.fraud.api.transaction.dto.TransactionEventReceiptResponse;
import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import com.example.fraud.common.event.TransactionEventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction Events", description = "Phase 2 contract-only transaction event APIs")
@RestController
@RequestMapping("/api/v1/transactions/events")
public class TransactionEventContractController {

    @Operation(
            summary = "Accept a transaction event contract",
            description = "Phase 2 skeleton only. Actual Kafka publish and receipt persistence are implemented in Phase 3.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Contract accepted by skeleton endpoint"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation failure",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionEventAcceptedResponse acceptContract(
            @Valid @RequestBody TransactionEventRequest request,
            HttpServletRequest servletRequest
    ) {
        return new TransactionEventAcceptedResponse(
                request.eventId(),
                "ACCEPTED",
                OffsetDateTime.now(),
                TraceIdResolver.resolve(servletRequest)
        );
    }

    @Operation(
            summary = "Get transaction event receipt contract",
            description = "Phase 2 stub response only. Actual PostgreSQL receipt lookup is implemented in Phase 3."
    )
    @GetMapping("/{eventId}")
    public TransactionEventReceiptResponse getReceiptContract(
            @PathVariable String eventId,
            HttpServletRequest servletRequest
    ) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-15T10:30:00+09:00");
        return new TransactionEventReceiptResponse(
                eventId,
                "user-1001",
                TransactionEventType.PAYMENT,
                new BigDecimal("1500000"),
                "KRW",
                "ACCEPTED",
                eventTime,
                eventTime.plusSeconds(1),
                TraceIdResolver.resolve(servletRequest)
        );
    }
}
