package com.example.fraud.consumer.processing;

import com.example.fraud.common.event.TransactionEventMessage;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EventProcessingLogService {

    private final EventProcessingLogRepository repository;
    private final Clock clock;

    public EventProcessingLogService(EventProcessingLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ProcessingLogResult recordProcessedEvent(
            TransactionEventMessage message,
            String topic,
            int partitionNo,
            long offsetNo,
            String consumerGroupId
    ) {
        if (repository.existsByTopicAndPartitionNoAndOffsetNo(topic, partitionNo, offsetNo)) {
            return ProcessingLogResult.duplicate();
        }

        EventProcessingLogEntity entity = EventProcessingLogEntity.processed(
                message,
                topic,
                partitionNo,
                offsetNo,
                consumerGroupId,
                OffsetDateTime.now(clock)
        );

        try {
            repository.saveAndFlush(entity);
            return ProcessingLogResult.processed();
        } catch (DataIntegrityViolationException exception) {
            return ProcessingLogResult.duplicate();
        }
    }
}
