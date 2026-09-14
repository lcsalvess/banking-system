package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.UserRequestDTO;
import com.lucas.sistemabancario.dto.response.UserResponseDTO;
import com.lucas.sistemabancario.entity.User;
import com.lucas.sistemabancario.exception.user.UserEmailAlreadyExistsException;
import com.lucas.sistemabancario.exception.user.UsernameAlreadyExistsException;
import com.lucas.sistemabancario.repository.UserRepository;
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
