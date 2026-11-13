package com.taskmanager.service;

import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.dto.CreateTaskRequest;
import com.taskmanager.dto.PageResponse;
import com.taskmanager.dto.TaskFilter;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.dto.UpdateTaskRequest;
import com.taskmanager.exception.BadRequestException;
import com.taskmanager.exception.ResourceNotFoundException;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.UserPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public TaskResponse createTask(UserPrincipal principal, CreateTaskRequest request) {
        Task task = new Task();
        task.setUserId(principal.getId());
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setDueDate(request.dueDate() != null ? request.dueDate() : LocalDate.now());
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.PENDING);
        return TaskResponse.from(taskRepository.save(task));
    }

    public PageResponse<TaskResponse> getMyTasks(UserPrincipal principal, TaskFilter filter, Pageable pageable) {
        validateFilter(filter);
        Page<Task> page = taskRepository.search(principal.getId(), filter, pageable);
        return PageResponse.from(page.map(TaskResponse::from));
    }

    /** Admin view of another user's tasks. */
    public PageResponse<TaskResponse> getTasksForUser(String userId, TaskFilter filter, Pageable pageable) {
        validateFilter(filter);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        Page<Task> page = taskRepository.search(userId, filter, pageable);
        return PageResponse.from(page.map(TaskResponse::from));
    }

    public List<TaskResponse> getTodayTasks(UserPrincipal principal) {
        TaskFilter filter = new TaskFilter(LocalDate.now(), null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "createdAt"));
        return taskRepository.search(principal.getId(), filter, pageable)
                .map(TaskResponse::from)
                .getContent();
    }

    public TaskResponse getTask(UserPrincipal principal, String id) {
        return TaskResponse.from(findOwnedTask(principal, id));
    }

    public TaskResponse updateTask(UserPrincipal principal, String id, UpdateTaskRequest request) {
        Task task = findOwnedTask(principal, id);

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new BadRequestException("Title must not be blank");
            }
            task.setTitle(title);
        }
        if (request.description() != null) {
            task.setDescription(trimToNull(request.description()));
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.status() != null) {
            applyStatus(task, request.status());
        }

        return TaskResponse.from(taskRepository.save(task));
    }

    public TaskResponse updateStatus(UserPrincipal principal, String id, TaskStatus status) {
        Task task = findOwnedTask(principal, id);
        applyStatus(task, status);
        return TaskResponse.from(taskRepository.save(task));
    }

    public void deleteTask(UserPrincipal principal, String id) {
        Task task = findOwnedTask(principal, id);
        taskRepository.delete(task);
    }

    private Task findOwnedTask(UserPrincipal principal, String id) {
        return taskRepository.findByIdAndUserId(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    private void applyStatus(Task task, TaskStatus status) {
        if (status == task.getStatus()) {
            return;
        }
        task.setStatus(status);
        task.setCompletedAt(status == TaskStatus.COMPLETED ? Instant.now() : null);
    }

    private void validateFilter(TaskFilter filter) {
        if (filter != null && filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("'from' date must not be after 'to' date");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
