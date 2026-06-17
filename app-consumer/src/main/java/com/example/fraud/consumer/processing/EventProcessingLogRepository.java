package com.example.fraud.consumer.processing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventProcessingLogRepository extends JpaRepository<EventProcessingLogEntity, Long> {

    boolean existsByTopicAndPartitionNoAndOffsetNo(String topic, int partitionNo, long offsetNo);

    List<EventProcessingLogEntity> findByEventIdOrderByProcessedAtDesc(String eventId);
}
