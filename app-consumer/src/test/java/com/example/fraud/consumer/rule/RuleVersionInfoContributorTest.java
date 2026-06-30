package com.example.fraud.consumer.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class RuleVersionInfoContributorTest {

    private final RuleVersionInfoContributor contributor = new RuleVersionInfoContributor();

    @Test
    void exposesActiveRuleVersionInActuatorInfoDetails() {
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        Info info = builder.build();
        assertThat(info.getDetails()).containsKey("fraudRule");
        assertThat(info.getDetails().get("fraudRule"))
                .isEqualTo(Map.of(
                        "activeRuleVersion", FraudRuleVersions.ACTIVE_RULE_VERSION,
                        "versionSource", "app-consumer",
                        "scope", "fraud-rule-engine-baseline"
                ));
    }

    @Test
    void doesNotExposeHighCardinalityIdentifiers() {
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        @SuppressWarnings("unchecked")
        Map<String, Object> fraudRule = (Map<String, Object>) builder.build().getDetails().get("fraudRule");
        assertThat(fraudRule).doesNotContainKeys("eventId", "userId", "transactionId", "traceId");
    }
}
