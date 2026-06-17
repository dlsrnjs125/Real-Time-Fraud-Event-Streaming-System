package com.example.fraud.api.admin.processing;

import com.example.fraud.api.admin.dto.ProcessingLogItemResponse;
import com.example.fraud.api.admin.dto.ProcessingLogResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingLogQueryService {

    private final EventProcessingLogRepository repository;

    public ProcessingLogQueryService(EventProcessingLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProcessingLogResponse getProcessingLog(String eventId) {
        var logs = repository.findByEventIdOrderByProcessedAtDesc(eventId).stream()
                .map(log -> new ProcessingLogItemResponse(
                        log.getEventId(),
                        log.getTraceId(),
                        log.getUserId(),
                        log.getTopic(),
                        log.getPartitionNo(),
                        log.getOffsetNo(),
                        log.getConsumerGroupId(),
                        log.getStatus().name(),
                        log.getReceivedAt(),
                        log.getProcessedAt(),
                        log.getErrorMessage()
                ))
                .toList();

        return new ProcessingLogResponse(eventId, logs);
    }
}
