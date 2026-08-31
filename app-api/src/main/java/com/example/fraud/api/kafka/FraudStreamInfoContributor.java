package com.example.fraud.api.kafka;

import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class FraudStreamInfoContributor implements InfoContributor {

    static final String DETAIL_NAME = "fraudStream";

    private final FraudStreamProperties properties;

    public FraudStreamInfoContributor(FraudStreamProperties properties) {
        this.properties = properties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_NAME, Map.of(
                "mode", properties.mode().name(),
                "producerTopic", properties.resolvedProducerTopic()
        ));
    }
}
