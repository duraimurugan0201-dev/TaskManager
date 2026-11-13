package com.taskmanager.repository;

import com.taskmanager.domain.Task;
import com.taskmanager.dto.TaskFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepositoryImpl implements TaskRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public TaskRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Task> search(String userId, TaskFilter filter, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("userId").is(userId));

        if (filter != null) {
            if (filter.date() != null) {
                filters.add(Criteria.where("dueDate").is(filter.date()));
            } else {
                if (filter.from() != null) {
                    filters.add(Criteria.where("dueDate").gte(filter.from()));
                }
                if (filter.to() != null) {
                    filters.add(Criteria.where("dueDate").lte(filter.to()));
                }
            }
            if (filter.status() != null) {
                filters.add(Criteria.where("status").is(filter.status().name()));
            }
            if (filter.priority() != null) {
                filters.add(Criteria.where("priority").is(filter.priority().name()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String term = Pattern.quote(filter.search().trim());
                filters.add(new Criteria().orOperator(
                        Criteria.where("title").regex(term, "i"),
                        Criteria.where("description").regex(term, "i")));
            }
        }

        Criteria criteria = new Criteria().andOperator(filters);
        Query query = new Query(criteria).with(pageable);

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Task.class);
        List<Task> content = mongoTemplate.find(query, Task.class);
        return new PageImpl<>(content, pageable, total);
    }
}
