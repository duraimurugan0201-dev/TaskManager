package com.taskmanager.dto;

import com.taskmanager.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(
        @NotNull(message = "Status is required")
        TaskStatus status) {
}
