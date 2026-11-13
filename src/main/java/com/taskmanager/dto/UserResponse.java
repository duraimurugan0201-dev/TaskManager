package com.taskmanager.dto;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
import java.time.Instant;
import java.util.Set;

/** Public representation of a user; never contains the password hash. */
public record UserResponse(
        String id,
        String username,
        String name,
        String phoneNumber,
        String email,
        Set<Role> roles,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRoles(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
