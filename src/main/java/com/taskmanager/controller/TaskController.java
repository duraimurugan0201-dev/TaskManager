package com.taskmanager.controller;

import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.dto.ApiResponse;
import com.taskmanager.dto.CreateTaskRequest;
import com.taskmanager.dto.PageResponse;
import com.taskmanager.dto.TaskFilter;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.TaskStatusRequest;
import com.taskmanager.dto.UpdateTaskRequest;
import com.taskmanager.security.UserPrincipal;
import com.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Daily-task endpoints. Every operation is scoped to the authenticated user:
 * a user can only ever read or modify their own tasks.
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Create, list, fetch, update and delete your daily tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a daily task", description = "dueDate defaults to today; priority defaults to MEDIUM")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.createTask(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created successfully", created));
    }

    @GetMapping
    @Operation(summary = "List my tasks",
            description = "Paginated tasks with optional date/status/priority/search filters. Use date for a single day, or from/to for a range.")
    public ApiResponse<PageResponse<TaskResponse>> listTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        TaskFilter filter = new TaskFilter(date, from, to, status, priority, search);
        return ApiResponse.success(taskService.getMyTasks(principal, filter, pageable));
    }

    @GetMapping("/today")
    @Operation(summary = "My tasks for today")
    public ApiResponse<List<TaskResponse>> todayTasks(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(taskService.getTodayTasks(principal));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of my tasks by id")
    public ApiResponse<TaskResponse> getTask(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable String id) {
        return ApiResponse.success(taskService.getTask(principal, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update my task", description = "Partial update; only non-null fields are applied")
    public ApiResponse<TaskResponse> updateTask(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String id,
                                                @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.success("Task updated successfully", taskService.updateTask(principal, id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change my task status", description = "Setting status to COMPLETED stamps completedAt automatically")
    public ApiResponse<TaskResponse> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable String id,
                                                  @Valid @RequestBody TaskStatusRequest request) {
        return ApiResponse.success("Task status updated successfully",
                taskService.updateStatus(principal, id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my tasks")
    public ApiResponse<Void> deleteTask(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable String id) {
        taskService.deleteTask(principal, id);
        return ApiResponse.success("Task deleted successfully", null);
    }
}
