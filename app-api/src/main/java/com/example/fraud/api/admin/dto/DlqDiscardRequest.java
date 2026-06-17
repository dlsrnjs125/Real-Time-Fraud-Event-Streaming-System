package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "DLQ discard request")
public record DlqDiscardRequest(
        @NotBlank
        String operatorId,

        @NotBlank
        String reason
) {
}
