package com.lucas.bankingsystem.repository;

import com.lucas.bankingsystem.entity.User;
import com.lucas.bankingsystem.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
