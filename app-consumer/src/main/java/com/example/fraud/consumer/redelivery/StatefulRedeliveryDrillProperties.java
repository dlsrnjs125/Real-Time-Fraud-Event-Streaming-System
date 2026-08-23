package com.example.fraud.consumer.redelivery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud.consumer.redelivery-drill")
public record StatefulRedeliveryDrillProperties(
        boolean enabled,
        String eventId,
        StatefulRedeliveryFailurePoint failurePoint,
        Boolean failOnce
) {

    public StatefulRedeliveryDrillProperties {
        if (eventId == null) {
            eventId = "";
        }
        if (failurePoint == null) {
            failurePoint = StatefulRedeliveryFailurePoint.NONE;
        }
        if (failOnce == null) {
            failOnce = true;
        }
    }

    public static StatefulRedeliveryDrillProperties disabled() {
        return new StatefulRedeliveryDrillProperties(
                false,
                "",
                StatefulRedeliveryFailurePoint.NONE,
                true
        );
    }
}
