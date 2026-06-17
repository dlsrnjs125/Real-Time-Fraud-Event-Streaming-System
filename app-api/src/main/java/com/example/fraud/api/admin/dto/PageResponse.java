package com.example.fraud.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Page response")
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements
) {
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0);
    }
}
