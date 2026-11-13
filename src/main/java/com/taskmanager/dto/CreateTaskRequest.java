package com.taskmanager.dto;

import com.taskmanager.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload for creating a daily task for the authenticated user. */
public record CreateTaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        /** Defaults to today when omitted. */
        LocalDate dueDate,

        /** Defaults to MEDIUM when omitted. */
        TaskPriority priority) {
}
