package com.taskmanager.dto;

import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Partial update of a daily task. Only non-null fields are applied. */
public record UpdateTaskRequest(
        @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        LocalDate dueDate,

        TaskPriority priority,

        TaskStatus status) {
}
