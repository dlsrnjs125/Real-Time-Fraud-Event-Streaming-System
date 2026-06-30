package com.example.fraud.api.admin.fraud;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FraudDetectionResultRepository extends JpaRepository<FraudDetectionResultEntity, Long> {

    Optional<FraudDetectionResultEntity> findByEventId(String eventId);

    @Query("""
            select result.ruleVersion as ruleVersion, count(result) as resultCount
            from FraudDetectionResultEntity result
            group by result.ruleVersion
            """)
    List<RuleVersionCount> countByRuleVersion();

    interface RuleVersionCount {

        String getRuleVersion();

        long getResultCount();
    }
}
