package com.example.fraud.api.support.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudEventStreamingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Real-Time Fraud Event Streaming System API")
                        .version("v1")
                        .description("Phase 2 contract-only API documentation. Kafka publish, DB persistence, Consumer processing, and DLQ reprocessing are implemented in later phases."));
    }
}
