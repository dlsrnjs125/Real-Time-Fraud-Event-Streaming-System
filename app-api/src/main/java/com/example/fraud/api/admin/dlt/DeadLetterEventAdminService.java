package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.admin.dto.DlqDiscardResponse;
import com.example.fraud.api.admin.dto.DlqEventSummaryResponse;
import com.example.fraud.api.admin.dto.DlqReprocessResponse;
import com.example.fraud.api.admin.dto.PageResponse;
import com.example.fraud.common.event.TransactionEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterEventAdminService {

    private final DeadLetterEventRepository repository;
    private final DeadLetterReprocessPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeadLetterEventAdminService(
            DeadLetterEventRepository repository,
            DeadLetterReprocessPublisher publisher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
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

    @Transactional
    public DlqReprocessResponse reprocess(long id, String traceId) {
        DeadLetterEventEntity event = repository.findById(id)
                .orElseThrow(() -> new DeadLetterEventNotFoundException(id));
        OffsetDateTime now = OffsetDateTime.now(clock);
        event.startReprocessing(now);
        TransactionEventMessage payload = readPayload(event);
        try {
            publisher.publish(payload);
            event.markReprocessed(now);
        } catch (DeadLetterPublishFailedException exception) {
            event.markReprocessFailed(now);
        }
        return new DlqReprocessResponse(
                event.getId(),
                event.getStatus().name(),
                String.valueOf(event.getReprocessAttempts()),
                traceId
        );
    }

    @Transactional
    public DlqDiscardResponse discard(long id, String reason, String traceId) {
        DeadLetterEventEntity event = repository.findById(id)
                .orElseThrow(() -> new DeadLetterEventNotFoundException(id));
        event.discard(reason, OffsetDateTime.now(clock));
        return new DlqDiscardResponse(event.getId(), event.getStatus().name(), traceId);
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
