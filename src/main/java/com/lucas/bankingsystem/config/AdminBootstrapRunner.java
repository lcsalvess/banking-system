package com.lucas.bankingsystem.config;

import com.lucas.bankingsystem.entity.User;
import com.lucas.bankingsystem.entity.enums.Role;
import com.lucas.bankingsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Usuário ADMIN já existe. Bootstrap ignorado.");
            return;
        }
        if (adminUsername.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Nenhum ADMIN encontrado e ADMIN_USERNAME/ADMIN_EMAIL/ADMIN_PASSWORD não foram definidos. Bootstrap ignorado.");
            return;
        }
        User admin = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);
        log.info("Usuário ADMIN inicial criado: {}", adminUsername);
    }
}
