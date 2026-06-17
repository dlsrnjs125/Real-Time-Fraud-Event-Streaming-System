package com.example.fraud.api.admin;

import com.example.fraud.api.admin.dto.DlqDiscardRequest;
import com.example.fraud.api.admin.dto.DlqDiscardResponse;
import com.example.fraud.api.admin.dto.DlqEventSummaryResponse;
import com.example.fraud.api.admin.dto.DlqReprocessRequest;
import com.example.fraud.api.admin.dto.DlqReprocessResponse;
import com.example.fraud.api.admin.dto.FraudResultDetailResponse;
import com.example.fraud.api.admin.dto.FraudResultSummaryResponse;
import com.example.fraud.api.admin.dto.FraudRuleListResponse;
import com.example.fraud.api.admin.dto.FraudRuleResponse;
import com.example.fraud.api.admin.dto.OperationSummaryResponse;
import com.example.fraud.api.admin.dto.PageResponse;
import com.example.fraud.api.admin.dto.ProcessingLogResponse;
import com.example.fraud.api.support.exception.ErrorResponse;
import com.example.fraud.api.support.logging.TraceIdResolver;
import com.example.fraud.common.event.FraudRuleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Contracts", description = "Phase 2 contract-only admin APIs")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminContractController {

    @Operation(summary = "List fraud results", description = "Phase 2 empty stub. Actual query is implemented in Phase 5.")
    @GetMapping("/fraud-results")
    public PageResponse<FraudResultSummaryResponse> listFraudResults(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.empty(page, size);
    }

    @Operation(summary = "Get fraud result detail", description = "Phase 2 contract-only stub. Actual query is implemented in Phase 5.")
    @GetMapping("/fraud-results/{eventId}")
    public FraudResultDetailResponse getFraudResult(@PathVariable String eventId, HttpServletRequest request) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-15T10:30:00+09:00");
        return new FraudResultDetailResponse(
                eventId,
                "user-1001",
                null,
                0,
                List.of(),
                List.of(),
                false,
                List.of(),
                eventTime,
                eventTime.plusSeconds(1),
                eventTime.plusSeconds(2),
                1000,
                2000,
                TraceIdResolver.resolve(request)
        );
    }

    @Operation(summary = "List fraud rules", description = "Phase 2 static contract. Actual rule execution is implemented in later phases.")
    @GetMapping("/fraud-rules")
    public FraudRuleListResponse listFraudRules() {
        return new FraudRuleListResponse(List.of(
                new FraudRuleResponse(FraudRuleCode.HIGH_AMOUNT, true, 40, "amount >= 1000000 KRW", false),
                new FraudRuleResponse(FraudRuleCode.VELOCITY, true, 35, "5 transactions within 60 seconds", true)
        ));
    }

    @Operation(summary = "List DLQ events", description = "Phase 2 empty stub. Actual DLQ query is implemented in Phase 9.")
    @GetMapping("/dlq-events")
    public PageResponse<DlqEventSummaryResponse> listDlqEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.empty(page, size);
    }

    @Operation(
            summary = "Reprocess DLQ event",
            description = "Phase 2 contract-only stub. Actual safe reprocessing is implemented in Phase 9.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Contract stub response"),
                    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping("/dlq-events/{dlqId}/reprocess")
    public DlqReprocessResponse reprocessDlqEvent(
            @PathVariable long dlqId,
            @Valid @RequestBody DlqReprocessRequest request,
            HttpServletRequest servletRequest
    ) {
        return new DlqReprocessResponse(
                dlqId,
                "REPROCESSING",
                "attempt-contract-only",
                TraceIdResolver.resolve(servletRequest)
        );
    }

    @Operation(summary = "Discard DLQ event", description = "Phase 2 contract-only stub. Actual discard flow is implemented in Phase 9.")
    @PatchMapping("/dlq-events/{dlqId}/discard")
    public DlqDiscardResponse discardDlqEvent(
            @PathVariable long dlqId,
            @Valid @RequestBody DlqDiscardRequest request,
            HttpServletRequest servletRequest
    ) {
        return new DlqDiscardResponse(dlqId, "DISCARDED", TraceIdResolver.resolve(servletRequest));
    }

    @Operation(summary = "Get event processing log", description = "Phase 2 empty stub. Actual processing log query is implemented in Phase 4.")
    @GetMapping("/events/{eventId}/processing-log")
    public ProcessingLogResponse getProcessingLog(@PathVariable String eventId) {
        return new ProcessingLogResponse(eventId, List.of());
    }

    @Operation(summary = "Get operational summary", description = "Phase 2 zero-value stub. Actual metrics summary is implemented in Phase 10.")
    @GetMapping("/operations/summary")
    public OperationSummaryResponse getOperationSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        return new OperationSummaryResponse(now.minusHours(1), now, 0, 0, 0, 0, 0, 0);
    }
}
