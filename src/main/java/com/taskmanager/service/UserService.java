package com.taskmanager.service;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
import com.taskmanager.dto.CreateUserRequest;
import com.taskmanager.dto.PageResponse;
import com.taskmanager.dto.UpdateUserRequest;
import com.taskmanager.dto.UserResponse;
import com.taskmanager.exception.BadRequestException;
import com.taskmanager.exception.DuplicateResourceException;
import com.taskmanager.exception.ResourceNotFoundException;
import com.taskmanager.repository.TaskRepository;
import com.taskmanager.repository.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       TaskRepository taskRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createUser(CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists: " + username);
        }

        String email = normalizeEmail(request.email());
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email already in use: " + email);
        }

        Set<Role> roles = (request.roles() == null || request.roles().isEmpty())
                ? EnumSet.of(Role.USER)
                : EnumSet.copyOf(request.roles());

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setPhoneNumber(request.phoneNumber().trim());
        user.setEmail(email);
        user.setRoles(roles);
        user.setEnabled(true);

        return UserResponse.from(userRepository.save(user));
    }

    public PageResponse<UserResponse> listUsers(String search, Role role, Boolean enabled, Pageable pageable) {
        Page<User> page = userRepository.search(search, role, enabled, pageable);
        return PageResponse.from(page.map(UserResponse::from));
    }

    public UserResponse getUser(String id) {
        return UserResponse.from(findUser(id));
    }

    public UserResponse updateUser(String id, UpdateUserRequest request) {
        User user = findUser(id);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Name must not be blank");
            }
            user.setName(name);
        }

        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }

        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (email != null) {
                userRepository.findByEmailIgnoreCase(email)
                        .filter(existing -> !existing.getId().equals(user.getId()))
                        .ifPresent(existing -> {
                            throw new DuplicateResourceException("Email already in use: " + email);
                        });
            }
            user.setEmail(email);
        }

        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRoles(EnumSet.copyOf(request.roles()));
        }

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        return UserResponse.from(userRepository.save(user));
    }

    /** Deletes a user and all of their tasks. An admin cannot delete themselves. */
    public void deleteUser(String id, String requestingUserId) {
        if (id.equals(requestingUserId)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = findUser(id);
        taskRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    private User findUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }
}
