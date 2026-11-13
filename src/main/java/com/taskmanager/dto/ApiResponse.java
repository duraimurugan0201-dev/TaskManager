package com.taskmanager.dto;

import java.time.Instant;

/**
 * Uniform success envelope returned by every endpoint.
 *
 * <pre>
 * { "success": true, "message": "...", "data": { ... }, "timestamp": "..." }
 * </pre>
 */
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Request successful", data, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }
}
