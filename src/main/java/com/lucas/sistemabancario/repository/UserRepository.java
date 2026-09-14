package com.lucas.sistemabancario.repository;

import com.lucas.sistemabancario.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
