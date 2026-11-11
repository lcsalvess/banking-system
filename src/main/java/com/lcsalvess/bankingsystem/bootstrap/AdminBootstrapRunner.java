package com.lcsalvess.bankingsystem.bootstrap;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.generator.EmployeeIdGenerator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    @Value("${app.admin.full-name:}")
    private String adminFullName;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.bootstrap.required:false}")
    private boolean bootstrapRequired;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            EmployeeIdGenerator employeeIdGenerator,
            PasswordEncoder passwordEncoder,
            Validator validator
    ) {
        this.userRepository = userRepository;
        this.employeeIdGenerator = employeeIdGenerator;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    public void run(String @NonNull ... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Administrator user already exists. Bootstrap skipped.");
            return;
        }

        if (!credentialsConfigured()) {
            String message =
                    "No administrator exists and the initial credentials "
                            + "have not been configured.";

            if (bootstrapRequired) {
                throw new IllegalStateException(
                        message + " Configure ADMIN_FULL_NAME, ADMIN_USERNAME, "
                                + "ADMIN_EMAIL and ADMIN_PASSWORD before starting the application."
                );
            }

            log.warn("{} Bootstrap skipped in this environment.", message);
            return;
        }

        validateCredentials();

        String employeeId = employeeIdGenerator.generate();

        User admin = User.createHuman(
                employeeId,
                adminFullName,
                adminUsername,
                adminEmail,
                passwordEncoder.encode(adminPassword),
                Role.ADMIN
        );

        userRepository.save(admin);

        log.info("Initial administrator user created successfully.");
    }

    private void validateCredentials() {
        UserRequestDTO candidate = new UserRequestDTO(
                adminFullName,
                adminUsername,
                adminEmail,
                adminPassword,
                Role.ADMIN
        );

        Set<ConstraintViolation<UserRequestDTO>> violations =
                validator.validate(candidate);

        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining(" | "));

            throw new IllegalStateException(
                    "Initial administrator credentials are invalid: " + details
            );
        }
    }

    private boolean credentialsConfigured() {
        return adminFullName != null && !adminFullName.isBlank()
                && adminUsername != null && !adminUsername.isBlank()
                && adminEmail != null && !adminEmail.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }
}
