package com.example.fraud.api.admin;

import com.example.fraud.api.admin.dto.DlqDiscardRequest;
import com.example.fraud.api.admin.dto.DlqDiscardResponse;
import com.example.fraud.api.admin.dto.DlqEventSummaryResponse;
import com.example.fraud.api.admin.dto.DlqReprocessRequest;
import com.example.fraud.api.admin.dto.DlqReprocessResponse;
import com.example.fraud.api.admin.dto.FraudDetectionResultResponse;
import com.example.fraud.api.admin.dto.FraudResultDetailResponse;
import com.example.fraud.api.admin.dto.FraudResultSummaryResponse;
import com.example.fraud.api.admin.dto.FraudRuleListResponse;
import com.example.fraud.api.admin.dto.FraudRuleResponse;
import com.example.fraud.api.admin.dto.FraudRuleResultResponse;
import com.example.fraud.api.admin.dto.FraudRuleVersionSummaryResponse;
import com.example.fraud.api.admin.dto.OperationSummaryResponse;
import com.example.fraud.api.admin.dto.PageResponse;
import com.example.fraud.api.admin.dto.ProcessingLogResponse;
import com.example.fraud.api.admin.dlt.DeadLetterEventAdminService;
import com.example.fraud.api.admin.dlt.DeadLetterEventDetailResponse;
import com.example.fraud.api.admin.dlt.DeadLetterStatus;
import com.example.fraud.api.admin.fraud.FraudDetectionResultQueryService;
import com.example.fraud.api.admin.processing.ProcessingLogQueryService;
import com.example.fraud.api.support.exception.ErrorResponse;
import com.example.fraud.api.support.logging.TraceIdResolver;
import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.RiskLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin APIs", description = "Admin query APIs for local development and verification")
@SecurityRequirement(name = "adminToken")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminContractController {

    private final ProcessingLogQueryService processingLogQueryService;
    private final FraudDetectionResultQueryService fraudDetectionResultQueryService;
    private final DeadLetterEventAdminService deadLetterEventAdminService;

    public AdminContractController(
            ProcessingLogQueryService processingLogQueryService,
            FraudDetectionResultQueryService fraudDetectionResultQueryService,
            DeadLetterEventAdminService deadLetterEventAdminService
    ) {
        this.processingLogQueryService = processingLogQueryService;
        this.fraudDetectionResultQueryService = fraudDetectionResultQueryService;
        this.deadLetterEventAdminService = deadLetterEventAdminService;
    }

    @Operation(summary = "List fraud results", description = "Phase 2 empty stub. Actual query is implemented in Phase 5.")
    @GetMapping("/fraud-results")
    public PageResponse<FraudResultSummaryResponse> listFraudResults(
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Boolean degraded,
            @RequestParam(required = false) FraudRuleCode ruleCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.empty(page, size);
    }

    @Operation(
            summary = "Summarize fraud result rule versions",
            description = "Counts stored fraud detection results by non-null ruleVersion and reports legacy missing rows separately."
    )
    @GetMapping("/fraud-results/rule-version-summary")
    public FraudRuleVersionSummaryResponse getFraudResultRuleVersionSummary() {
        return fraudDetectionResultQueryService.getRuleVersionSummary();
    }

    @Operation(summary = "Get fraud result detail", description = "Phase 2 contract-only stub. Actual query is implemented in Phase 5.")
    @GetMapping("/fraud-results/{eventId}")
    public FraudResultDetailResponse getFraudResult(@PathVariable String eventId, HttpServletRequest request) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-15T10:30:00+09:00");
        return new FraudResultDetailResponse(
                eventId,
                "user-1001",
                RiskLevel.HIGH,
                75,
                List.of(FraudRuleCode.HIGH_AMOUNT, FraudRuleCode.VELOCITY),
                List.of(),
                false,
                List.of(
                        new FraudRuleResultResponse(
                                FraudRuleCode.HIGH_AMOUNT,
                                true,
                                false,
                                40,
                                "amount >= 1000000 KRW"
                        ),
                        new FraudRuleResultResponse(
                                FraudRuleCode.VELOCITY,
                                true,
                                false,
                                35,
                                "5 transactions within 60 seconds"
                        )
                ),
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

    @Operation(summary = "List DLT events", description = "Lists dead letter events for operational recovery.")
    @GetMapping({"/dlq-events", "/dlt-events"})
    public PageResponse<DlqEventSummaryResponse> listDlqEvents(
            @RequestParam(required = false) DeadLetterStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return deadLetterEventAdminService.list(status, page, size);
    }

    @Operation(summary = "Get DLT event detail", description = "Returns a dead letter event including stored payload JSON.")
    @GetMapping({"/dlq-events/{dlqId}", "/dlt-events/{dlqId}"})
    public DeadLetterEventDetailResponse getDlqEvent(@PathVariable long dlqId) {
        return deadLetterEventAdminService.get(dlqId);
    }

    @Operation(
            summary = "Reprocess DLQ event",
            description = "Phase 2 contract-only stub. Actual safe reprocessing is implemented in Phase 9.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reprocess publish succeeded"),
                    @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping({"/dlq-events/{dlqId}/reprocess", "/dlt-events/{dlqId}/reprocess"})
    public DlqReprocessResponse reprocessDlqEvent(
            @PathVariable long dlqId,
            @Valid @RequestBody DlqReprocessRequest request,
            HttpServletRequest servletRequest
    ) {
        return deadLetterEventAdminService.reprocess(
                dlqId,
                request.operatorId(),
                request.reason(),
                TraceIdResolver.resolve(servletRequest)
        );
    }

    @Operation(summary = "Discard DLT event", description = "Marks a dead letter event as discarded with an operator reason.")
    @PostMapping({"/dlq-events/{dlqId}/discard", "/dlt-events/{dlqId}/discard"})
    public DlqDiscardResponse discardDlqEvent(
            @PathVariable long dlqId,
            @Valid @RequestBody DlqDiscardRequest request,
            HttpServletRequest servletRequest
    ) {
        return deadLetterEventAdminService.discard(
                dlqId,
                request.operatorId(),
                request.reason(),
                TraceIdResolver.resolve(servletRequest)
        );
    }

    @Operation(summary = "Get event processing log", description = "Looks up Consumer processing logs by eventId.")
    @GetMapping("/events/{eventId}/processing-log")
    public ProcessingLogResponse getProcessingLog(@PathVariable String eventId) {
        return processingLogQueryService.getProcessingLog(eventId);
    }

    @Operation(summary = "Get event fraud result", description = "Looks up fraud detection result by eventId.")
    @GetMapping("/events/{eventId}/fraud-result")
    public FraudDetectionResultResponse getEventFraudResult(@PathVariable String eventId) {
        return fraudDetectionResultQueryService.getByEventId(eventId);
    }

    @Operation(summary = "Get operational summary", description = "Phase 2 zero-value stub. Actual metrics summary is implemented in Phase 10.")
    @GetMapping("/operations/summary")
    public OperationSummaryResponse getOperationSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        return new OperationSummaryResponse(now.minusHours(1), now, 0, 0, 0, 0, 0, 0);
    }
}
