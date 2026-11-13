package com.taskmanager.repository;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Dynamic user queries that derived query methods cannot express. */
public interface UserRepositoryCustom {

    /**
     * @param search  optional case-insensitive match on username/name/phone/email
     * @param role    optional role filter
     * @param enabled optional enabled flag filter
     */
    Page<User> search(String search, Role role, Boolean enabled, Pageable pageable);
}
