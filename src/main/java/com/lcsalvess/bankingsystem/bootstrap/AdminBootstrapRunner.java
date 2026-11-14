package com.lcsalvess.bankingsystem.bootstrap;

import com.lcsalvess.bankingsystem.config.bootstrap.AdminBootstrapProperties;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;
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
    private final AdminBootstrapProperties adminBootstrapProperties;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            EmployeeIdGenerator employeeIdGenerator,
            PasswordEncoder passwordEncoder,
            Validator validator,
            AdminBootstrapProperties adminBootstrapProperties
    ) {
        this.userRepository = userRepository;
        this.employeeIdGenerator = employeeIdGenerator;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.adminBootstrapProperties = adminBootstrapProperties;
    }

    @Override
    public void run(String @NonNull ... args) {
        if (userRepository.existsByRoleAndActiveTrue(Role.ADMIN)) {
            log.info("An active administrator already exists. Bootstrap skipped.");
            return;
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            throw new IllegalStateException(
                    "Administrator bootstrap cannot proceed because administrator "
                            + "accounts exist, but none are active. "
                            + "Restore administrator access using the documented "
                            + "recovery procedure before restarting the application."
            );
        }

        if (!credentialsConfigured()) {
            String message =
                    "No administrator exists and the initial credentials "
                            + "have not been configured.";

            if (adminBootstrapProperties.bootstrap().required()) {
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

        String rawPassword = Objects.requireNonNull(
                adminBootstrapProperties.password(),
                "Administrator password is required."
        );

        String encodedPassword = Objects.requireNonNull(
                passwordEncoder.encode(rawPassword),
                "Password encoder returned a null encoded password."
        );

        User admin = User.createHuman(
                employeeId,
                adminBootstrapProperties.fullName(),
                adminBootstrapProperties.username(),
                adminBootstrapProperties.email(),
                encodedPassword,
                Role.ADMIN
        );

        userRepository.save(admin);

        log.info("Initial administrator user created successfully.");
    }

    private void validateCredentials() {
        UserRequestDTO candidate = new UserRequestDTO(
                adminBootstrapProperties.fullName(),
                adminBootstrapProperties.username(),
                adminBootstrapProperties.email(),
                adminBootstrapProperties.password(),
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
        return adminBootstrapProperties.fullName() != null
                && !adminBootstrapProperties.fullName().isBlank()
                && adminBootstrapProperties.username() != null
                && !adminBootstrapProperties.username().isBlank()
                && adminBootstrapProperties.email() != null
                && !adminBootstrapProperties.email().isBlank()
                && adminBootstrapProperties.password() != null
                && !adminBootstrapProperties.password().isBlank();
    }
}
