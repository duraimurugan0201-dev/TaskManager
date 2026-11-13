package com.taskmanager.repository;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
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
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public UserRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<User> search(String search, Role role, Boolean enabled, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            String term = Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("username").regex(term, "i"),
                    Criteria.where("name").regex(term, "i"),
                    Criteria.where("phoneNumber").regex(term, "i"),
                    Criteria.where("email").regex(term, "i")));
        }
        if (role != null) {
            filters.add(Criteria.where("roles").is(role.name()));
        }
        if (enabled != null) {
            filters.add(Criteria.where("enabled").is(enabled));
        }

        Criteria criteria = filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
        Query query = new Query(criteria).with(pageable);

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), User.class);
        List<User> content = mongoTemplate.find(query, User.class);
        return new PageImpl<>(content, pageable, total);
    }
}
