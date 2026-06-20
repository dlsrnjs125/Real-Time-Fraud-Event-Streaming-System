package com.example.fraud.consumer.fraud;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDetectionResultRepository extends JpaRepository<FraudDetectionResultEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<FraudDetectionResultEntity> findByEventId(String eventId);
}
