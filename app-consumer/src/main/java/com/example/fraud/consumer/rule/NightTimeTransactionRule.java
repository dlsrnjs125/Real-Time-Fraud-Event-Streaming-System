package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.FraudRuleCode;
import com.example.fraud.common.event.TransactionEventMessage;
import org.springframework.stereotype.Component;

@Component
public class NightTimeTransactionRule implements FraudRule {

    static final int SCORE = 20;

    @Override
    public FraudRuleEvaluation evaluate(TransactionEventMessage message) {
        int hour = message.eventTime().getHour();
        boolean matched = hour >= 0 && hour <= 5;
        return new FraudRuleEvaluation(
                FraudRuleCode.NIGHT_TIME_TRANSACTION,
                matched,
                matched ? SCORE : 0,
                matched ? "eventTime hour between 0 and 5" : "eventTime outside night window"
        );
    }
}
