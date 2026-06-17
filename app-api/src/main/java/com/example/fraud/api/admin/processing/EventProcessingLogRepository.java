package com.example.fraud.api.admin.processing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventProcessingLogRepository extends JpaRepository<EventProcessingLogEntity, Long> {

    List<EventProcessingLogEntity> findByEventIdOrderByProcessedAtDesc(String eventId);
}
