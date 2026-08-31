package com.example.fraud.consumer.redis;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.kafka.FraudStreamProperties;
import com.example.fraud.consumer.metrics.FraudConsumerMetrics;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisRecentTransactionWindowStore implements RecentTransactionWindowStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRecentTransactionWindowStore.class);
    private static final String KEY_PREFIX = "fraud:tx:";

    private final StringRedisTemplate redisTemplate;
    private final SlidingWindowProperties properties;
    private final FraudStreamProperties streamProperties;
    private final FraudConsumerMetrics metrics;

    public RedisRecentTransactionWindowStore(
            StringRedisTemplate redisTemplate,
            SlidingWindowProperties properties,
            FraudStreamProperties streamProperties,
            FraudConsumerMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.streamProperties = streamProperties;
        this.metrics = metrics;
    }

    @Override
    public RecentTransactionWindowResult recordAndGetWindow(TransactionEventMessage message) {
        return metrics.recordRedisWindowLatency(() -> recordAndGetWindowWithMetrics(message));
    }

    private RecentTransactionWindowResult recordAndGetWindowWithMetrics(TransactionEventMessage message) {
        try {
            return recordAndGetWindowOrThrow(message);
        } catch (RuntimeException exception) {
            metrics.incrementRedisDegraded();
            log.warn(
                    "redis sliding window degraded traceId={} eventId={} userId={} reason={}",
                    message.traceId(),
                    message.eventId(),
                    message.userId(),
                    exception.getClass().getSimpleName()
            );
            return RecentTransactionWindowResult.redisUnavailable("Redis sliding window unavailable");
        }
    }

    private RecentTransactionWindowResult recordAndGetWindowOrThrow(TransactionEventMessage message) {
        if (isTooLateForLiveWindow(message)) {
            metrics.incrementRedisWindowTooLate();
            log.info(
                    "redis sliding window skipped too-late event traceId={} eventId={} userId={} latenessMs={} allowedLatenessMs={}",
                    message.traceId(),
                    message.eventId(),
                    message.userId(),
                    lateness(message).toMillis(),
                    properties.allowedLateness().toMillis()
            );
            return RecentTransactionWindowResult.tooLate("Event exceeded allowed lateness; Redis sliding window skipped");
        }

        long eventTimeMillis = message.eventTime().toInstant().toEpochMilli();
        long windowStartMillis = message.eventTime()
                .minus(properties.window())
                .toInstant()
                .toEpochMilli();
        String userEventsKey = userEventsKey(message.userId());
        String eventKey = eventKey(message.eventId());

        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        HashOperations<String, Object, Object> hash = redisTemplate.opsForHash();

        hash.putAll(eventKey, Map.of(
                "amount", message.amount().toPlainString(),
                "currency", message.currency(),
                "eventTime", message.eventTime().toString(),
                "userId", message.userId()
        ));
        zSet.add(userEventsKey, message.eventId(), eventTimeMillis);
        zSet.removeRangeByScore(userEventsKey, 0, windowStartMillis - 1);
        redisTemplate.expire(userEventsKey, properties.ttl());
        redisTemplate.expire(eventKey, properties.ttl());

        Set<String> eventIds = zSet.rangeByScore(userEventsKey, windowStartMillis, eventTimeMillis);
        if (eventIds == null || eventIds.isEmpty()) {
            metrics.recordRedisWindowState(0, BigDecimal.ZERO);
            return RecentTransactionWindowResult.normal(0, BigDecimal.ZERO);
        }

        List<BigDecimal> validAmounts = eventIds.stream()
                .map(id -> hash.get(eventKey(id), "amount"))
                .filter(value -> value != null)
                .map(Object::toString)
                .map(BigDecimal::new)
                .toList();
        BigDecimal amountSum = validAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.recordRedisWindowState(validAmounts.size(), amountSum);

        return RecentTransactionWindowResult.normal(validAmounts.size(), amountSum);
    }

    private boolean isTooLateForLiveWindow(TransactionEventMessage message) {
        if (streamProperties.replay()) {
            return false;
        }
        if (message.eventTime() == null || message.receivedAt() == null) {
            return false;
        }
        Duration eventLateness = lateness(message);
        return !eventLateness.isNegative() && eventLateness.compareTo(properties.allowedLateness()) > 0;
    }

    private Duration lateness(TransactionEventMessage message) {
        return Duration.between(message.eventTime().toInstant(), message.receivedAt().toInstant());
    }

    private String userEventsKey(String userId) {
        return KEY_PREFIX + properties.namespace() + ":user:" + userId + ":events";
    }

    private String eventKey(String eventId) {
        return KEY_PREFIX + properties.namespace() + ":event:" + eventId;
    }
}
