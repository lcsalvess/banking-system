package com.lcsalvess.bankingsystem.service.user.generator;

import com.lcsalvess.bankingsystem.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresEmployeeIdGenerator implements EmployeeIdGenerator {

    private final UserRepository userRepository;

    public PostgresEmployeeIdGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public String generate() {
        return userRepository.nextEmployeeId();
    }
}
