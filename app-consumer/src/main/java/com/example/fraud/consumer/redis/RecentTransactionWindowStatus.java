package com.example.fraud.consumer.redis;

public enum RecentTransactionWindowStatus {
    NORMAL,
    REDIS_UNAVAILABLE,
    TOO_LATE
}
