package com.example.fraud.consumer.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class RedisRecentTransactionWindowStoreTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSet = mock();
    private final HashOperations<String, Object, Object> hash = mock();
    private final RedisRecentTransactionWindowStore store = new RedisRecentTransactionWindowStore(
            redisTemplate,
            new SlidingWindowProperties(
                    Duration.ofMinutes(5),
                    5,
                    BigDecimal.valueOf(3_000_000),
                    Duration.ofMinutes(10)
            )
    );

    @Test
    void recordsEventAndReturnsWindowCountAndAmountSum() {
        TransactionEventMessage message = message("evt-redis-001", BigDecimal.valueOf(1_200_000));
        mockRedisOperations();
        when(zSet.rangeByScore(
                "fraud:tx:user:user-1001:events",
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).thenReturn(Set.of("evt-redis-001", "evt-redis-previous"));
        when(hash.get("fraud:tx:event:evt-redis-001", "amount")).thenReturn("1200000");
        when(hash.get("fraud:tx:event:evt-redis-previous", "amount")).thenReturn("900000");

        RecentTransactionWindowResult result = store.recordAndGetWindow(message);

        assertThat(result.degraded()).isFalse();
        assertThat(result.transactionCount()).isEqualTo(2);
        assertThat(result.amountSum()).isEqualByComparingTo("2100000");
        verify(zSet).add("fraud:tx:user:user-1001:events", "evt-redis-001", millis("2026-06-19T10:00:00Z"));
        verify(hash).putAll(eq("fraud:tx:event:evt-redis-001"), anyMap());
        verify(zSet).removeRangeByScore("fraud:tx:user:user-1001:events", 0, millis("2026-06-19T09:55:00Z") - 1);
        verify(redisTemplate).expire("fraud:tx:user:user-1001:events", Duration.ofMinutes(10));
        verify(redisTemplate).expire("fraud:tx:event:evt-redis-001", Duration.ofMinutes(10));
    }

    @Test
    void storesHashMetadataBeforeAddingEventToUserWindow() {
        TransactionEventMessage message = message("evt-redis-001", BigDecimal.valueOf(1_200_000));
        mockRedisOperations();
        when(zSet.rangeByScore(
                "fraud:tx:user:user-1001:events",
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).thenReturn(Set.of("evt-redis-001"));
        when(hash.get("fraud:tx:event:evt-redis-001", "amount")).thenReturn("1200000");

        store.recordAndGetWindow(message);

        InOrder inOrder = inOrder(hash, zSet);
        inOrder.verify(hash).putAll(eq("fraud:tx:event:evt-redis-001"), anyMap());
        inOrder.verify(zSet).add("fraud:tx:user:user-1001:events", "evt-redis-001", millis("2026-06-19T10:00:00Z"));
    }

    @Test
    void sameEventIdDoesNotIncreaseCountWhenRedisReturnsSingleMember() {
        TransactionEventMessage message = message("evt-redis-duplicate", BigDecimal.valueOf(100_000));
        mockRedisOperations();
        when(zSet.rangeByScore(
                "fraud:tx:user:user-1001:events",
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).thenReturn(Set.of("evt-redis-duplicate"));
        when(hash.get("fraud:tx:event:evt-redis-duplicate", "amount")).thenReturn("100000");

        RecentTransactionWindowResult first = store.recordAndGetWindow(message);
        RecentTransactionWindowResult second = store.recordAndGetWindow(message);

        assertThat(first.transactionCount()).isEqualTo(1);
        assertThat(second.transactionCount()).isEqualTo(1);
        verify(zSet, org.mockito.Mockito.times(2))
                .add("fraud:tx:user:user-1001:events", "evt-redis-duplicate", millis("2026-06-19T10:00:00Z"));
    }

    @Test
    void excludesEventsOutsideWindowFromReturnedCountAndAmount() {
        TransactionEventMessage message = message("evt-redis-new", BigDecimal.valueOf(200_000));
        mockRedisOperations();
        when(zSet.rangeByScore(
                "fraud:tx:user:user-1001:events",
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).thenReturn(Set.of("evt-redis-new"));
        when(hash.get("fraud:tx:event:evt-redis-new", "amount")).thenReturn("200000");

        RecentTransactionWindowResult result = store.recordAndGetWindow(message);

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("200000");
    }

    @Test
    void excludesWindowMembersWithoutAmountMetadataFromCountAndAmount() {
        TransactionEventMessage message = message("evt-redis-new", BigDecimal.valueOf(200_000));
        mockRedisOperations();
        when(zSet.rangeByScore(
                "fraud:tx:user:user-1001:events",
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).thenReturn(Set.of("evt-redis-new", "evt-redis-partial"));
        when(hash.get("fraud:tx:event:evt-redis-new", "amount")).thenReturn("200000");
        when(hash.get("fraud:tx:event:evt-redis-partial", "amount")).thenReturn(null);

        RecentTransactionWindowResult result = store.recordAndGetWindow(message);

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("200000");
    }

    @Test
    void returnsDegradedResultWhenRedisFails() {
        TransactionEventMessage message = message("evt-redis-fail", BigDecimal.valueOf(100_000));
        when(redisTemplate.opsForZSet()).thenThrow(new RedisConnectionFailureException("redis down"));

        RecentTransactionWindowResult result = store.recordAndGetWindow(message);

        assertThat(result.degraded()).isTrue();
        assertThat(result.transactionCount()).isZero();
        assertThat(result.amountSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.reason()).contains("Redis sliding window unavailable");
    }

    private void mockRedisOperations() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(redisTemplate.opsForHash()).thenReturn(hash);
        when(zSet.add(eq("fraud:tx:user:user-1001:events"), eq("evt-redis-001"), anyDouble()))
                .thenReturn(true);
        when(zSet.add(eq("fraud:tx:user:user-1001:events"), eq("evt-redis-duplicate"), anyDouble()))
                .thenReturn(true);
        when(zSet.add(eq("fraud:tx:user:user-1001:events"), eq("evt-redis-new"), anyDouble()))
                .thenReturn(true);
    }

    private TransactionEventMessage message(String eventId, BigDecimal amount) {
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-06-19T10:00:00Z");
        return new TransactionEventMessage(
                "v1",
                eventId,
                "user-1001",
                "acc-1001",
                TransactionEventType.PAYMENT,
                amount,
                "KRW",
                "merchant-001",
                "device-001",
                "SEOUL",
                eventTime,
                eventTime.plusSeconds(1),
                "trace-" + eventId
        );
    }

    private double millis(String time) {
        return OffsetDateTime.parse(time).toInstant().toEpochMilli();
    }
}
