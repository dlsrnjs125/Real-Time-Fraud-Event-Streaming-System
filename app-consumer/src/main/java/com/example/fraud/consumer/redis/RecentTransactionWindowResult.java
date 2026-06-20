package com.example.fraud.consumer.redis;

import java.math.BigDecimal;

public record RecentTransactionWindowResult(
        boolean degraded,
        int transactionCount,
        BigDecimal amountSum,
        String reason
) {

    public static RecentTransactionWindowResult normal(int transactionCount, BigDecimal amountSum) {
        return new RecentTransactionWindowResult(false, transactionCount, amountSum, null);
    }

    public static RecentTransactionWindowResult degraded(String reason) {
        return new RecentTransactionWindowResult(true, 0, BigDecimal.ZERO, reason);
    }
}
