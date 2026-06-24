package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "DLQ discard request")
public record DlqDiscardRequest(
        @NotBlank
        @Size(max = 100)
        String operatorId,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
