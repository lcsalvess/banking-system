package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.UserRequestDTO;
import com.lucas.bankingsystem.dto.response.UserResponseDTO;
import com.lucas.bankingsystem.entity.User;
import com.lucas.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lucas.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lucas.bankingsystem.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO dto) {
        if (userRepository.existsByUsername(dto.username())) {
            throw new UsernameAlreadyExistsException("Nome de usuário já cadastrado: " + dto.username());
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new UserEmailAlreadyExistsException("E-mail já cadastrado: " + dto.email());
        }
        User user = new User(dto.username(),
                dto.email(),
                passwordEncoder.encode(dto.password()),
                dto.role());
        User savedUser = userRepository.save(user);
        return new UserResponseDTO(
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.isActive()
        );
    }
}
