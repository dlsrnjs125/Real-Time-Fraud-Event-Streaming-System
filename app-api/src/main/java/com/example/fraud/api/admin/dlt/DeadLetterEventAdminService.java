package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.admin.audit.AdminAction;
import com.example.fraud.api.admin.audit.AdminAuditLogService;
import com.example.fraud.api.admin.audit.AdminAuditResult;
import com.example.fraud.api.admin.dto.DlqDiscardResponse;
import com.example.fraud.api.admin.dto.DlqEventSummaryResponse;
import com.example.fraud.api.admin.dto.DlqReprocessResponse;
import com.example.fraud.api.admin.dto.PageResponse;
import com.example.fraud.api.support.exception.ApiException;
import com.example.fraud.common.event.TransactionEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterEventAdminService {

    private final DeadLetterEventRepository repository;
    private final DeadLetterReprocessPublisher publisher;
    private final AdminAuditLogService auditLogService;
    private final DltReprocessProperties reprocessProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DeadLetterAdminMetrics metrics;

    public DeadLetterEventAdminService(
            DeadLetterEventRepository repository,
            DeadLetterReprocessPublisher publisher,
            AdminAuditLogService auditLogService,
            DltReprocessProperties reprocessProperties,
            ObjectMapper objectMapper,
            Clock clock,
            DeadLetterAdminMetrics metrics
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.auditLogService = auditLogService;
        this.reprocessProperties = reprocessProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public PageResponse<DlqEventSummaryResponse> list(DeadLetterStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(this::toSummary).toList(),
                page,
                size,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public DeadLetterEventDetailResponse get(long id) {
        return repository.findById(id)
                .map(this::toDetail)
                .orElseThrow(() -> new DeadLetterEventNotFoundException(id));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public DlqReprocessResponse reprocess(long id, String actor, String reason, String traceId) {
        DeadLetterEventEntity event = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new DeadLetterEventNotFoundException(id));
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            if (event.getReprocessAttempts() >= reprocessProperties.getMaxAttempts()) {
                throw new DeadLetterMaxAttemptsExceededException(
                        event.getId(),
                        event.getReprocessAttempts(),
                        reprocessProperties.getMaxAttempts()
                );
            }
            event.startReprocessing(now);
            TransactionEventMessage payload = readPayload(event);
            publisher.publish(payload);
            event.markReprocessed(now);
            recordReprocessAudit(event, actor, reason, traceId, AdminAuditResult.SUCCESS, "reprocessed");
            metrics.incrementReprocessRequested("success");
        } catch (DeadLetterPublishFailedException exception) {
            event.markReprocessFailed(now);
            recordReprocessAudit(event, actor, reason, traceId, AdminAuditResult.FAILED, "publish_failed");
            metrics.incrementReprocessRequested("failed");
            throw exception;
        } catch (ApiException exception) {
            recordReprocessAudit(event, actor, reason, traceId, AdminAuditResult.FAILED, exception.errorCode().name());
            metrics.incrementReprocessRequested("failed");
            throw exception;
        }
        return new DlqReprocessResponse(
                event.getId(),
                event.getStatus().name(),
                String.valueOf(event.getReprocessAttempts()),
                traceId
        );
    }

    @Transactional(noRollbackFor = ApiException.class)
    public DlqDiscardResponse discard(long id, String actor, String reason, String traceId) {
        DeadLetterEventEntity event = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new DeadLetterEventNotFoundException(id));
        try {
            event.discard(reason, OffsetDateTime.now(clock));
            recordDiscardAudit(event, actor, reason, traceId, AdminAuditResult.SUCCESS, "discarded");
            metrics.incrementDiscarded("success");
        } catch (ApiException exception) {
            recordDiscardAudit(event, actor, reason, traceId, AdminAuditResult.FAILED, exception.errorCode().name());
            metrics.incrementDiscarded("failed");
            throw exception;
        }
        return new DlqDiscardResponse(event.getId(), event.getStatus().name(), traceId);
    }

    private void recordReprocessAudit(
            DeadLetterEventEntity event,
            String actor,
            String reason,
            String traceId,
            AdminAuditResult result,
            String resultReason
    ) {
        auditLogService.recordDltAction(
                actor,
                AdminAction.DLT_REPROCESS,
                event.getId(),
                event.getEventId(),
                traceId,
                result,
                reason,
                Map.of(
                        "eventId", event.getEventId(),
                        "status", event.getStatus().name(),
                        "reprocessAttempts", event.getReprocessAttempts(),
                        "maxAttempts", reprocessProperties.getMaxAttempts(),
                        "resultReason", resultReason
                )
        );
    }

    private void recordDiscardAudit(
            DeadLetterEventEntity event,
            String actor,
            String reason,
            String traceId,
            AdminAuditResult result,
            String resultReason
    ) {
        auditLogService.recordDltAction(
                actor,
                AdminAction.DLT_DISCARD,
                event.getId(),
                event.getEventId(),
                traceId,
                result,
                reason,
                Map.of(
                        "eventId", event.getEventId(),
                        "status", event.getStatus().name(),
                        "resultReason", resultReason
                )
        );
    }

    private TransactionEventMessage readPayload(DeadLetterEventEntity event) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), TransactionEventMessage.class);
        } catch (JsonProcessingException exception) {
            throw new DeadLetterStateConflictException(event.getId(), event.getStatus());
        }
    }

    private DlqEventSummaryResponse toSummary(DeadLetterEventEntity event) {
        return new DlqEventSummaryResponse(
                event.getId(),
                event.getEventId(),
                event.getSourceTopic(),
                event.getSourcePartition(),
                event.getSourceOffset(),
                event.getFailureStage(),
                event.getStatus().name(),
                null,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private DeadLetterEventDetailResponse toDetail(DeadLetterEventEntity event) {
        return new DeadLetterEventDetailResponse(
                event.getId(),
                event.getEventId(),
                event.getTraceId(),
                event.getUserId(),
                event.getSourceTopic(),
                event.getSourcePartition(),
                event.getSourceOffset(),
                event.getDltTopic(),
                event.getFailureStage(),
                event.getErrorType(),
                event.getErrorMessage(),
                event.getPayloadJson(),
                event.getStatus(),
                event.getReprocessAttempts(),
                event.getLastReprocessedAt(),
                event.getDiscardedAt(),
                event.getDiscardReason(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
