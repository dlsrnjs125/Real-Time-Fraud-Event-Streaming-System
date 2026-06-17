package com.example.fraud.api.transaction;

import com.example.fraud.api.support.exception.ErrorResponse;
import com.example.fraud.api.support.logging.TraceIdResolver;
import com.example.fraud.api.transaction.application.TransactionEventIntakeService;
import com.example.fraud.api.transaction.dto.TransactionEventAcceptedResponse;
import com.example.fraud.api.transaction.dto.TransactionEventReceiptResponse;
import com.example.fraud.api.transaction.dto.TransactionEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction Events", description = "Transaction event intake APIs")
@RestController
@RequestMapping("/api/v1/transactions/events")
public class TransactionEventController {

    private final TransactionEventIntakeService intakeService;

    public TransactionEventController(TransactionEventIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @Operation(
            summary = "Accept and publish a transaction event",
            description = "Validates the request, stores a transaction_event_receipt, and publishes a TransactionEventMessage to Kafka transaction-events with userId as key.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted and published"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation failure",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Duplicate eventId",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Kafka publish failure",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionEventAcceptedResponse accept(
            @Valid @RequestBody TransactionEventRequest request,
            HttpServletRequest servletRequest
    ) {
        return intakeService.accept(request, TraceIdResolver.resolve(servletRequest));
    }

    @Operation(
            summary = "Get transaction event receipt",
            description = "Looks up a persisted transaction_event_receipt by eventId."
    )
    @GetMapping("/{eventId}")
    public TransactionEventReceiptResponse getReceipt(@PathVariable String eventId) {
        return intakeService.getReceipt(eventId);
    }
}
