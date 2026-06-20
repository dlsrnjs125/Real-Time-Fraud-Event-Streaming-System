package com.example.fraud.consumer.fraud;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.rule.FraudRuleEngineResult;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class FraudDetectionResultService {

    private final FraudDetectionResultRepository repository;
    private final Clock clock;

    public FraudDetectionResultService(FraudDetectionResultRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public FraudDetectionResultSaveResult saveResult(
            TransactionEventMessage message,
            FraudRuleEngineResult ruleResult
    ) {
        if (repository.existsByEventId(message.eventId())) {
            return FraudDetectionResultSaveResult.duplicate();
        }

        FraudDetectionResultEntity entity = FraudDetectionResultEntity.from(
                message,
                ruleResult,
                OffsetDateTime.now(clock)
        );

        try {
            repository.saveAndFlush(entity);
            return FraudDetectionResultSaveResult.saved();
        } catch (DataIntegrityViolationException exception) {
            return FraudDetectionResultSaveResult.duplicate();
        }
    }

    public boolean existsResultForEventId(String eventId) {
        return repository.existsByEventId(eventId);
    }
}
