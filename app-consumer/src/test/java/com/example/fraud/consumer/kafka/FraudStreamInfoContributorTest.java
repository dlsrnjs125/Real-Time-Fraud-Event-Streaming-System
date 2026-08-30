package com.example.fraud.consumer.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fraud.consumer.redis.SlidingWindowProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class FraudStreamInfoContributorTest {

    @Test
    void exposesReplayConsumerRouting() {
        FraudStreamInfoContributor contributor = new FraudStreamInfoContributor(
                new FraudStreamProperties(FraudStreamMode.REPLAY),
                new SlidingWindowProperties(
                        Duration.ofMinutes(5),
                        5,
                        BigDecimal.valueOf(3_000_000),
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        "replay"
                ),
                KafkaTopicNames.TRANSACTION_EVENTS_REPLAY,
                "fraud-event-replay-consumer"
        );
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) builder.build().getDetails()
                .get(FraudStreamInfoContributor.DETAIL_NAME);
        assertThat(detail)
                .containsEntry("mode", "REPLAY")
                .containsEntry("consumerTopic", KafkaTopicNames.TRANSACTION_EVENTS_REPLAY)
                .containsEntry("consumerGroupId", "fraud-event-replay-consumer")
                .containsEntry("redisNamespace", "replay");
    }
}
