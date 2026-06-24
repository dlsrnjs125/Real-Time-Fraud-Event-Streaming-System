package com.example.fraud.api.support.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudEventStreamingOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("adminToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Token")))
                .info(new Info()
                        .title("Real-Time Fraud Event Streaming System API")
                        .version("v1")
                        .description("Phase 14 API documentation. Transaction event intake, Kafka publish, Consumer processing, Redis degraded handling, DLT reprocessing, and local/dev admin token protection are implemented."));
    }
}
