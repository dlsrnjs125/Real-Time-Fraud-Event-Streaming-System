package com.example.fraud.api.admin.dlt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, Long> {

    Page<DeadLetterEventEntity> findByStatus(DeadLetterStatus status, Pageable pageable);
}
