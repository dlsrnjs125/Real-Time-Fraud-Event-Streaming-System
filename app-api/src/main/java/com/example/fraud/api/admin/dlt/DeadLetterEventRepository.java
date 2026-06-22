package com.example.fraud.api.admin.dlt;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, Long> {

    Page<DeadLetterEventEntity> findByStatus(DeadLetterStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DeadLetterEventEntity e where e.id = :id")
    Optional<DeadLetterEventEntity> findByIdForUpdate(@Param("id") long id);
}
