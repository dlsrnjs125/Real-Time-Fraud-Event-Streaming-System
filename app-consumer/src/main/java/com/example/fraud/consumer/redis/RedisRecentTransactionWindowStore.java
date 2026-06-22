package com.example.fraud.consumer.redis;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.consumer.metrics.FraudConsumerMetrics;
import java.math.BigDecimal;
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
    private static final String USER_EVENTS_KEY_PREFIX = "fraud:tx:user:";
    private static final String EVENT_KEY_PREFIX = "fraud:tx:event:";

    private final StringRedisTemplate redisTemplate;
    private final SlidingWindowProperties properties;
    private final FraudConsumerMetrics metrics;

    public RedisRecentTransactionWindowStore(
            StringRedisTemplate redisTemplate,
            SlidingWindowProperties properties,
            FraudConsumerMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
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
            return RecentTransactionWindowResult.degraded("Redis sliding window unavailable");
        }
    }

    private RecentTransactionWindowResult recordAndGetWindowOrThrow(TransactionEventMessage message) {
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

        return RecentTransactionWindowResult.normal(validAmounts.size(), amountSum);
    }

    private String userEventsKey(String userId) {
        return USER_EVENTS_KEY_PREFIX + userId + ":events";
    }

    private String eventKey(String eventId) {
        return EVENT_KEY_PREFIX + eventId;
    }
}
