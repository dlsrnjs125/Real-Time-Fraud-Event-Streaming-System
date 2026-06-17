package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Fraud rule list response")
public record FraudRuleListResponse(
        List<FraudRuleResponse> items
) {
}
