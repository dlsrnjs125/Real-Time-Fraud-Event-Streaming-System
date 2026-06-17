package com.example.fraud.api.transaction.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionEventReceiptRepository extends JpaRepository<TransactionEventReceiptEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<TransactionEventReceiptEntity> findByEventId(String eventId);
}
