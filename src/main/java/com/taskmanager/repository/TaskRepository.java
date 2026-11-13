package com.taskmanager.repository;

import com.taskmanager.domain.Task;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<Task, String>, TaskRepositoryCustom {

    Optional<Task> findByIdAndUserId(String id, String userId);

    long deleteByUserId(String userId);
}
