package com.taskmanager.config;

import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
import com.taskmanager.repository.UserRepository;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account on startup when the configured username is free,
 * so a fresh database is immediately usable. Subsequent startups are no-ops.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminName;
    private final String adminPhone;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.username:admin}") String adminUsername,
                           @Value("${app.admin.password:Admin@12345}") String adminPassword,
                           @Value("${app.admin.name:System Administrator}") String adminName,
                           @Value("${app.admin.phone:9000000000}") String adminPhone) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
        this.adminPhone = adminPhone;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin bootstrap skipped: user '{}' already exists", adminUsername);
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setName(adminName);
        admin.setPhoneNumber(adminPhone);
        admin.setRoles(EnumSet.of(Role.ADMIN));
        admin.setEnabled(true);
        userRepository.save(admin);

        log.warn("Bootstrapped default admin '{}'. Change this password (or set ADMIN_PASSWORD) before exposing the API.",
                adminUsername);
    }
}
