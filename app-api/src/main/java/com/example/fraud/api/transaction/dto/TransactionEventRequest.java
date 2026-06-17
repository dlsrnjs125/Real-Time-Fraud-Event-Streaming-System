package com.example.fraud.api.transaction.dto;

import com.example.fraud.common.event.TransactionEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Transaction event intake request")
public record TransactionEventRequest(
        @NotBlank
        @Schema(example = "evt-20260615-000001")
        String eventId,

        @NotBlank
        @Schema(example = "user-1001")
        String userId,

        @NotBlank
        @Schema(example = "acc-3001")
        String accountId,

        @NotNull
        @Schema(example = "PAYMENT")
        TransactionEventType eventType,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Schema(example = "1500000")
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "KRW", message = "must be KRW")
        @Schema(example = "KRW")
        String currency,

        @Schema(example = "merchant-777")
        String merchantId,

        @Schema(example = "device-abc")
        String deviceId,

        @Schema(example = "KR")
        String location,

        @NotNull
        @Schema(example = "2026-06-15T10:30:00+09:00")
        OffsetDateTime eventTime
) {
}
