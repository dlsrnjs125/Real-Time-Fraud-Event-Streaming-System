package com.example.fraud.consumer.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.common.event.TransactionEventMessage;
import com.example.fraud.common.event.TransactionEventType;
import com.example.fraud.consumer.metrics.FraudConsumerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration")
class RedisRecentTransactionWindowStoreIntegrationTest {

    private static final int DEFAULT_TEST_DATABASE = 15;

    private static LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate redisTemplate;
    private RedisRecentTransactionWindowStore store;

    @BeforeAll
    static void startRedisConnection() {
        String host = System.getProperty("redis.integration.host", "localhost");
        int port = Integer.getInteger("redis.integration.port", 6379);
        int database = Integer.getInteger("redis.integration.database", DEFAULT_TEST_DATABASE);
        connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.setDatabase(database);
        connectionFactory.afterPropertiesSet();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
        } catch (RuntimeException exception) {
            connectionFactory.destroy();
            Assumptions.assumeTrue(
                    false,
                    "Redis integration test requires Redis at " + host + ":" + port + " db " + database
            );
        }
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate = new StringRedisTemplate(connectionFactory);
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        store = new RedisRecentTransactionWindowStore(
                redisTemplate,
                new SlidingWindowProperties(
                        Duration.ofMinutes(5),
                        5,
                        BigDecimal.valueOf(3_000_000),
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        "live"
                ),
                new FraudConsumerMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void recordsEventMetadataAndUserWindowInRedis() {
        TransactionEventMessage message = message(
                "evt-it-001",
                "user-it-001",
                BigDecimal.valueOf(1_200_000),
                "2026-06-19T10:00:00Z"
        );

        RecentTransactionWindowResult result = store.recordAndGetWindow(message);

        assertThat(result.degraded()).isFalse();
        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("1200000");
        assertThat(redisTemplate.opsForZSet().range(userEventsKey("user-it-001"), 0, -1))
                .containsExactly("evt-it-001");
        assertThat(redisTemplate.opsForHash().entries(eventKey("evt-it-001")))
                .containsEntry("amount", "1200000")
                .containsEntry("currency", "KRW")
                .containsEntry("userId", "user-it-001");
    }

    @Test
    void doesNotDoubleCountSameEventId() {
        TransactionEventMessage first = message(
                "evt-it-duplicate",
                "user-it-duplicate",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        );
        TransactionEventMessage second = message(
                "evt-it-duplicate",
                "user-it-duplicate",
                BigDecimal.valueOf(200_000),
                "2026-06-19T10:01:00Z"
        );

        store.recordAndGetWindow(first);
        RecentTransactionWindowResult result = store.recordAndGetWindow(second);

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("200000");
        assertThat(redisTemplate.opsForZSet().zCard(userEventsKey("user-it-duplicate"))).isEqualTo(1);
    }

    @Test
    void removesEventsOutsideWindow() {
        store.recordAndGetWindow(message(
                "evt-it-old",
                "user-it-cleanup",
                BigDecimal.valueOf(900_000),
                "2026-06-19T09:54:59Z"
        ));

        RecentTransactionWindowResult result = store.recordAndGetWindow(message(
                "evt-it-new",
                "user-it-cleanup",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("100000");
        assertThat(redisTemplate.opsForZSet().range(userEventsKey("user-it-cleanup"), 0, -1))
                .containsExactly("evt-it-new");
    }

    @Test
    void appliesTtlToUserWindowAndEventMetadata() {
        store.recordAndGetWindow(message(
                "evt-it-ttl",
                "user-it-ttl",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        ));

        assertThat(redisTemplate.getExpire(userEventsKey("user-it-ttl"))).isPositive();
        assertThat(redisTemplate.getExpire(eventKey("evt-it-ttl"))).isPositive();
    }

    @Test
    void excludesZsetMemberWithoutAmountMetadata() {
        String userEventsKey = userEventsKey("user-it-partial");
        redisTemplate.opsForZSet().add(
                userEventsKey,
                "evt-it-missing-metadata",
                millis("2026-06-19T09:59:00Z")
        );

        RecentTransactionWindowResult result = store.recordAndGetWindow(message(
                "evt-it-complete",
                "user-it-partial",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("100000");
    }

    @Test
    void calculatesWindowByEventTime() {
        store.recordAndGetWindow(message(
                "evt-it-future-seed",
                "user-it-event-time",
                BigDecimal.valueOf(500_000),
                "2026-06-19T10:10:00Z"
        ));

        RecentTransactionWindowResult result = store.recordAndGetWindow(message(
                "evt-it-current-window",
                "user-it-event-time",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        ));

        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("100000");
        assertThat(redisTemplate.opsForZSet().rangeByScore(
                userEventsKey("user-it-event-time"),
                millis("2026-06-19T09:55:00Z"),
                millis("2026-06-19T10:00:00Z")
        )).containsExactly("evt-it-current-window");
    }

    @Test
    void acceptsOutOfOrderEventWithinAllowedLatenessByEventTimeScore() {
        store.recordAndGetWindow(message(
                "evt-it-arrived-first",
                "user-it-out-of-order",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z",
                "2026-06-19T10:00:01Z"
        ));

        RecentTransactionWindowResult result = store.recordAndGetWindow(message(
                "evt-it-arrived-second",
                "user-it-out-of-order",
                BigDecimal.valueOf(200_000),
                "2026-06-19T09:58:00Z",
                "2026-06-19T10:01:00Z"
        ));

        assertThat(result.degraded()).isFalse();
        assertThat(result.transactionCount()).isEqualTo(1);
        assertThat(result.amountSum()).isEqualByComparingTo("200000");
        assertThat(redisTemplate.opsForZSet().range(userEventsKey("user-it-out-of-order"), 0, -1))
                .containsExactly("evt-it-arrived-second", "evt-it-arrived-first");
        assertThat(redisTemplate.opsForZSet().score(
                userEventsKey("user-it-out-of-order"),
                "evt-it-arrived-second"
        )).isLessThan(redisTemplate.opsForZSet().score(
                userEventsKey("user-it-out-of-order"),
                "evt-it-arrived-first"
        ));
    }

    @Test
    void skipsTooLateEventWithoutMutatingRedisState() {
        RecentTransactionWindowResult result = store.recordAndGetWindow(message(
                "evt-it-too-late",
                "user-it-too-late",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z",
                "2026-06-19T10:05:01Z"
        ));

        assertThat(result.degraded()).isTrue();
        assertThat(result.status()).isEqualTo(RecentTransactionWindowStatus.TOO_LATE);
        assertThat(result.reason()).contains("allowed lateness");
        assertThat(redisTemplate.opsForZSet().zCard(userEventsKey("user-it-too-late"))).isZero();
        assertThat(redisTemplate.hasKey(eventKey("evt-it-too-late"))).isFalse();
    }

    @Test
    void separatesLiveAndReplayNamespacesForSameUserAndEvent() {
        TransactionEventMessage message = message(
                "evt-it-replay-shared",
                "user-it-replay-shared",
                BigDecimal.valueOf(100_000),
                "2026-06-19T10:00:00Z"
        );
        RedisRecentTransactionWindowStore replayStore = new RedisRecentTransactionWindowStore(
                redisTemplate,
                new SlidingWindowProperties(
                        Duration.ofMinutes(5),
                        5,
                        BigDecimal.valueOf(3_000_000),
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        "replay"
                ),
                new FraudConsumerMetrics(new SimpleMeterRegistry())
        );

        store.recordAndGetWindow(message);
        replayStore.recordAndGetWindow(message);

        assertThat(redisTemplate.opsForZSet().range(userEventsKey("live", "user-it-replay-shared"), 0, -1))
                .containsExactly("evt-it-replay-shared");
        assertThat(redisTemplate.opsForZSet().range(userEventsKey("replay", "user-it-replay-shared"), 0, -1))
                .containsExactly("evt-it-replay-shared");
        assertThat(redisTemplate.hasKey(eventKey("live", "evt-it-replay-shared"))).isTrue();
        assertThat(redisTemplate.hasKey(eventKey("replay", "evt-it-replay-shared"))).isTrue();
    }

    private TransactionEventMessage message(
            String eventId,
            String userId,
            BigDecimal amount,
            String eventTime
    ) {
        OffsetDateTime time = OffsetDateTime.parse(eventTime);
        return message(eventId, userId, amount, eventTime, time.plusSeconds(1).toString());
    }

    private TransactionEventMessage message(
            String eventId,
            String userId,
            BigDecimal amount,
            String eventTime,
            String receivedAt
    ) {
        OffsetDateTime time = OffsetDateTime.parse(eventTime);
        return new TransactionEventMessage(
                "v1",
                eventId,
                userId,
                "acc-it-001",
                TransactionEventType.PAYMENT,
                amount,
                "KRW",
                "merchant-it-001",
                "device-it-001",
                "SEOUL",
                time,
                OffsetDateTime.parse(receivedAt),
                "trace-" + eventId
        );
    }

    private String userEventsKey(String userId) {
        return userEventsKey("live", userId);
    }

    private String eventKey(String eventId) {
        return eventKey("live", eventId);
    }

    private String userEventsKey(String namespace, String userId) {
        return "fraud:tx:" + namespace + ":user:" + userId + ":events";
    }

    private String eventKey(String namespace, String eventId) {
        return "fraud:tx:" + namespace + ":event:" + eventId;
    }

    private double millis(String time) {
        return OffsetDateTime.parse(time).toInstant().toEpochMilli();
    }
}
