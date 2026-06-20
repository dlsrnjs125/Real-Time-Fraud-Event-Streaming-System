package com.example.fraud.consumer.rule;

import com.example.fraud.common.event.TransactionEventMessage;

public interface FraudRule {

    FraudRuleEvaluation evaluate(TransactionEventMessage message);
}
