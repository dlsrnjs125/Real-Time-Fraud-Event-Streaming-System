package com.example.fraud.api.transaction.kafka;

import com.example.fraud.common.event.TransactionEventMessage;

public interface TransactionEventProducer {

    void publish(TransactionEventMessage message);
}
