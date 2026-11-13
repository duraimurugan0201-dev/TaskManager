package com.taskmanager.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned for every 4xx/5xx response. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
