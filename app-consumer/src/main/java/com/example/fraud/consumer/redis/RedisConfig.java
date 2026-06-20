package com.example.fraud.consumer.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlidingWindowProperties.class)
public class RedisConfig {
}
