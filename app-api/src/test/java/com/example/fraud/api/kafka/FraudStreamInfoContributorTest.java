package com.example.fraud.api.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class FraudStreamInfoContributorTest {

    @Test
    void exposesReplayProducerRouting() {
        FraudStreamInfoContributor contributor = new FraudStreamInfoContributor(
                new FraudStreamProperties(FraudStreamMode.REPLAY, null)
        );
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) builder.build().getDetails()
                .get(FraudStreamInfoContributor.DETAIL_NAME);
        assertThat(detail)
                .containsEntry("mode", "REPLAY")
                .containsEntry("producerTopic", KafkaTopicNames.TRANSACTION_EVENTS_REPLAY);
    }
}
