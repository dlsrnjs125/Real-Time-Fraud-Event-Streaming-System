package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Event processing log response")
public record ProcessingLogResponse(
        String eventId,
        List<ProcessingLogItemResponse> logs
) {
}
