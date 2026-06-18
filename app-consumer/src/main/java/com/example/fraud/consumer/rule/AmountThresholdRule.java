package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.TransactionEventMessage;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AmountThresholdRule implements FraudRule {

    static final BigDecimal THRESHOLD = BigDecimal.valueOf(1_000_000);
    static final int SCORE = 50;

    @Override
    public FraudRuleEvaluation evaluate(TransactionEventMessage message) {
        boolean matched = message.amount().compareTo(THRESHOLD) >= 0;
        return new FraudRuleEvaluation(
                FraudRuleCode.AMOUNT_THRESHOLD,
                matched,
                matched ? SCORE : 0,
                matched ? "amount >= 1000000 KRW" : "amount below threshold"
        );
    }
}
