package com.taskmanager.repository;

import com.taskmanager.domain.Task;
import com.taskmanager.dto.TaskFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Dynamic task queries scoped to a single owning user. */
public interface TaskRepositoryCustom {

    Page<Task> search(String userId, TaskFilter filter, Pageable pageable);
}
