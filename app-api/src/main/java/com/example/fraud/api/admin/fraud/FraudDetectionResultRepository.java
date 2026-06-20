package com.example.fraud.api.admin.fraud;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDetectionResultRepository extends JpaRepository<FraudDetectionResultEntity, Long> {

    Optional<FraudDetectionResultEntity> findByEventId(String eventId);
}
