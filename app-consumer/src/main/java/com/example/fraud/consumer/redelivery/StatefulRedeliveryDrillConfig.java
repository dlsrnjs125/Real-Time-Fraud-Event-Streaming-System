package com.example.fraud.consumer.redelivery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StatefulRedeliveryDrillProperties.class)
public class StatefulRedeliveryDrillConfig {
}
