package com.taskmanager.dto;

import com.taskmanager.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Admin-only partial update. Only non-null fields are applied.
 * Username is immutable.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        String name,

        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{6,19}$",
                message = "Phone number must be 7-20 characters: digits, spaces, dashes, optional leading +")
        String phoneNumber,

        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        Set<Role> roles,

        Boolean enabled) {
}
