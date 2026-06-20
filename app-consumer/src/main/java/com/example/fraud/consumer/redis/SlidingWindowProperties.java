package com.example.fraud.consumer.redis;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud.sliding-window")
public record SlidingWindowProperties(
        Duration window,
        int maxEvents,
        BigDecimal amountThreshold,
        Duration ttl
) {

    public SlidingWindowProperties {
        if (window == null) {
            window = Duration.ofMinutes(5);
        }
        if (maxEvents <= 0) {
            maxEvents = 5;
        }
        if (amountThreshold == null) {
            amountThreshold = BigDecimal.valueOf(3_000_000);
        }
        if (ttl == null) {
            ttl = Duration.ofMinutes(10);
        }
    }
}
