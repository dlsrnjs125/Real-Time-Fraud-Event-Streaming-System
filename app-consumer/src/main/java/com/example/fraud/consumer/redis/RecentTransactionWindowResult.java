package com.example.fraud.consumer.redis;

import java.math.BigDecimal;

public record RecentTransactionWindowResult(
        RecentTransactionWindowStatus status,
        int transactionCount,
        BigDecimal amountSum,
        String reason
) {

    public static RecentTransactionWindowResult normal(int transactionCount, BigDecimal amountSum) {
        return new RecentTransactionWindowResult(
                RecentTransactionWindowStatus.NORMAL,
                transactionCount,
                amountSum,
                null
        );
    }

    public static RecentTransactionWindowResult degraded(String reason) {
        return redisUnavailable(reason);
    }

    public static RecentTransactionWindowResult redisUnavailable(String reason) {
        return new RecentTransactionWindowResult(
                RecentTransactionWindowStatus.REDIS_UNAVAILABLE,
                0,
                BigDecimal.ZERO,
                reason
        );
    }

    public static RecentTransactionWindowResult tooLate(String reason) {
        return new RecentTransactionWindowResult(
                RecentTransactionWindowStatus.TOO_LATE,
                0,
                BigDecimal.ZERO,
                reason
        );
    }

    public boolean degraded() {
        return status != RecentTransactionWindowStatus.NORMAL;
    }
}
