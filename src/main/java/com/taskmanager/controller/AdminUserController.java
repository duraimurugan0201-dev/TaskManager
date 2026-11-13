package com.taskmanager.controller;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.dto.ApiResponse;
import com.taskmanager.dto.CreateUserRequest;
import com.taskmanager.dto.PageResponse;
import com.taskmanager.dto.TaskFilter;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.UpdateUserRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.security.UserPrincipal;
import com.taskmanager.service.TaskService;
import com.taskmanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "Admin-only user management")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;
    private final TaskService taskService;

    public AdminUserController(UserService userService, TaskService taskService) {
        this.userService = userService;
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a user", description = "Creates a user account. Roles default to USER when omitted.")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", created));
    }

    @GetMapping
    @Operation(summary = "List users", description = "Searchable, filterable, paginated user list")
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(userService.listUsers(search, role, enabled, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public ApiResponse<UserResponse> getUser(@PathVariable String id) {
        return ApiResponse.success(userService.getUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "Partial update; only non-null fields are applied. Username is immutable.")
    public ApiResponse<UserResponse> updateUser(@PathVariable String id,
                                                @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated successfully", userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Deletes the user and all tasks owned by them. Admins cannot delete themselves.")
    public ApiResponse<Void> deleteUser(@PathVariable String id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteUser(id, principal.getId());
        return ApiResponse.success("User and associated tasks deleted successfully", null);
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "Fetch a user's tasks", description = "Paginated tasks belonging to the given user")
    public ApiResponse<PageResponse<TaskResponse>> getUserTasks(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        TaskFilter filter = new TaskFilter(date, from, to, status, priority, search);
        return ApiResponse.success(taskService.getTasksForUser(id, filter, pageable));
    }
}
