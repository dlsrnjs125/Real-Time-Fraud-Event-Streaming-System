package com.example.fraud.consumer.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud.stream")
public record FraudStreamProperties(
        FraudStreamMode mode
) {

    public FraudStreamProperties {
        if (mode == null) {
            mode = FraudStreamMode.LIVE;
        }
    }

    public boolean replay() {
        return mode == FraudStreamMode.REPLAY;
    }
}
