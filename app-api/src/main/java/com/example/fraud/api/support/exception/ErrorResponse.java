package com.example.fraud.api.support.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Common error response")
public record ErrorResponse(
        @Schema(example = "INVALID_TRANSACTION_EVENT")
        String code,

        @Schema(example = "amount must be greater than zero")
        String message,

        @Schema(example = "trace-20260615-000001")
        String traceId
) {
}
