package com.example.fraud.consumer.kafka;

import com.example.fraud.consumer.redis.SlidingWindowProperties;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class FraudStreamInfoContributor implements InfoContributor {

    static final String DETAIL_NAME = "fraudStream";

    private final FraudStreamProperties streamProperties;
    private final SlidingWindowProperties slidingWindowProperties;
    private final String topic;
    private final String consumerGroupId;

    public FraudStreamInfoContributor(
            FraudStreamProperties streamProperties,
            SlidingWindowProperties slidingWindowProperties,
            @Value("${fraud.consumer.topic:" + KafkaTopicNames.TRANSACTION_EVENTS + "}") String topic,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroupId
    ) {
        this.streamProperties = streamProperties;
        this.slidingWindowProperties = slidingWindowProperties;
        this.topic = topic;
        this.consumerGroupId = consumerGroupId;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_NAME, Map.of(
                "mode", streamProperties.mode().name(),
                "consumerTopic", topic,
                "consumerGroupId", consumerGroupId,
                "redisNamespace", slidingWindowProperties.namespace()
        ));
    }
}
