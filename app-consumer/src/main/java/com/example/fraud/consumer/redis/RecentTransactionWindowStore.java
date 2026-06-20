package com.example.fraud.consumer.redis;

import com.example.fraud.common.event.TransactionEventMessage;

public interface RecentTransactionWindowStore {

    RecentTransactionWindowResult recordAndGetWindow(TransactionEventMessage message);
}
