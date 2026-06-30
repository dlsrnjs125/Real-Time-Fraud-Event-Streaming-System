package com.example.fraud.api.admin.fraud;

import com.example.fraud.api.admin.dto.FraudDetectionResultResponse;
import com.example.fraud.api.admin.dto.FraudRuleVersionSummaryResponse;
import com.example.fraud.common.event.FraudRuleCode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudDetectionResultQueryService {

    private final FraudDetectionResultRepository repository;

    public FraudDetectionResultQueryService(FraudDetectionResultRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FraudDetectionResultResponse getByEventId(String eventId) {
        FraudDetectionResultEntity result = repository.findByEventId(eventId)
                .orElseThrow(() -> new FraudResultNotFoundException(eventId));

        return new FraudDetectionResultResponse(
                result.getEventId(),
                result.getTraceId(),
                result.getUserId(),
                result.getAccountId(),
                result.getRuleVersion(),
                result.getRiskScore(),
                result.getRiskLevel(),
                result.getDecision(),
                parseMatchedRules(result.getMatchedRules()),
                parseMatchedRules(result.getSkippedRules()),
                result.isDegraded(),
                result.getReason(),
                result.getDetectedAt()
        );
    }

    @Transactional(readOnly = true)
    public FraudRuleVersionSummaryResponse getRuleVersionSummary() {
        Map<String, Long> resultCounts = new LinkedHashMap<>();
        long legacyMissingResults = 0;

        for (FraudDetectionResultRepository.RuleVersionCount count : repository.countByRuleVersion()) {
            if (count.getRuleVersion() == null || count.getRuleVersion().isBlank()) {
                legacyMissingResults += count.getResultCount();
            } else {
                resultCounts.put(count.getRuleVersion(), count.getResultCount());
            }
        }

        return new FraudRuleVersionSummaryResponse(resultCounts, legacyMissingResults);
    }

    private List<FraudRuleCode> parseMatchedRules(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(rule -> !rule.isBlank())
                .map(FraudRuleCode::valueOf)
                .toList();
    }
}
