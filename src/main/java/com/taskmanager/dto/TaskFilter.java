package com.taskmanager.dto;

import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import java.time.LocalDate;

/**
 * Internal filter for task queries. All fields are optional.
 *
 * @param date     exact due date match (daily view)
 * @param from     inclusive lower bound on due date
 * @param to       inclusive upper bound on due date
 * @param status   task status
 * @param priority task priority
 * @param search   case-insensitive substring match on title/description
 */
public record TaskFilter(
        LocalDate date,
        LocalDate from,
        LocalDate to,
        TaskStatus status,
        TaskPriority priority,
        String search) {
}
