package com.example.fraud.consumer.rule;

import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class RuleVersionInfoContributor implements InfoContributor {

    static final String DETAIL_NAME = "fraudRule";
    static final String VERSION_SOURCE = "app-consumer";
    static final String SCOPE = "fraud-rule-engine-baseline";

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_NAME, Map.of(
                "activeRuleVersion", FraudRuleVersions.ACTIVE_RULE_VERSION,
                "versionSource", VERSION_SOURCE,
                "scope", SCOPE
        ));
    }
}
