package com.example.fraud.consumer.dlt;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, Long> {

    boolean existsBySourceTopicAndSourcePartitionAndSourceOffset(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    );

    Optional<DeadLetterEventEntity> findBySourceTopicAndSourcePartitionAndSourceOffset(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    );
}
