package com.example.fraud.consumer.redelivery;

import com.example.fraud.common.event.TransactionEventMessage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StatefulRedeliveryFailureInjector {

    private static final Logger log = LoggerFactory.getLogger(StatefulRedeliveryFailureInjector.class);

    private final StatefulRedeliveryDrillProperties properties;
    private final Set<String> injectedFailures = ConcurrentHashMap.newKeySet();

    public StatefulRedeliveryFailureInjector(StatefulRedeliveryDrillProperties properties) {
        this.properties = properties;
    }

    public void failIfConfigured(StatefulRedeliveryFailurePoint failurePoint, TransactionEventMessage message) {
        if (!matches(failurePoint, message)) {
            return;
        }

        String failureKey = failurePoint.name() + ":" + message.eventId();
        if (properties.failOnce() && !injectedFailures.add(failureKey)) {
            return;
        }

        log.warn(
                "stateful redelivery drill failure injected traceId={} eventId={} userId={} failurePoint={}",
                message.traceId(),
                message.eventId(),
                message.userId(),
                failurePoint
        );
        pauseBeforeThrowIfConfigured();
        throw new StatefulRedeliveryDrillException(
                "Injected stateful redelivery failure at " + failurePoint + " for eventId=" + message.eventId()
        );
    }

    private void pauseBeforeThrowIfConfigured() {
        if (properties.pauseBeforeThrow().isZero() || properties.pauseBeforeThrow().isNegative()) {
            return;
        }
        try {
            Thread.sleep(properties.pauseBeforeThrow().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean matches(StatefulRedeliveryFailurePoint failurePoint, TransactionEventMessage message) {
        return properties.enabled()
                && properties.failurePoint() == failurePoint
                && !properties.eventId().isBlank()
                && properties.eventId().equals(message.eventId());
    }
}
