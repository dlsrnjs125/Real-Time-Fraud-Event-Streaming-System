package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.TransactionEventMessage;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SuspiciousLocationRule implements FraudRule {

    static final int SCORE = 30;
    private static final Set<String> SUSPICIOUS_LOCATIONS = Set.of("UNKNOWN", "FOREIGN", "HIGH_RISK");

    @Override
    public FraudRuleEvaluation evaluate(TransactionEventMessage message) {
        String location = message.location();
        boolean matched = location != null
                && SUSPICIOUS_LOCATIONS.contains(location.trim().toUpperCase(Locale.ROOT));
        return new FraudRuleEvaluation(
                FraudRuleCode.SUSPICIOUS_LOCATION,
                matched,
                matched ? SCORE : 0,
                matched ? "location is UNKNOWN, FOREIGN, or HIGH_RISK" : "location is not suspicious"
        );
    }
}
