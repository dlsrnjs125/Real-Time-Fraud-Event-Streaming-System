package com.example.fraud.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminApiSecurityStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminApiSecurityStartupLogger.class);
    private static final String DEFAULT_LOCAL_TOKEN = "local-admin-token";

    private final AdminApiSecurityProperties properties;

    public AdminApiSecurityStartupLogger(AdminApiSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (DEFAULT_LOCAL_TOKEN.equals(properties.getToken())) {
            log.warn("Default local admin token is enabled. Do not use this setting outside local/dev environment.");
        }
    }
}
