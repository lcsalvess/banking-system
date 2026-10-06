package com.lucas.bankingsystem.service.user;

import com.lucas.bankingsystem.dto.request.UserRequestDTO;
import com.lucas.bankingsystem.dto.response.UserResponseDTO;
import com.lucas.bankingsystem.entity.User;
import com.lucas.bankingsystem.event.user.UserOperationEvent;
import com.lucas.bankingsystem.event.user.UserOperationType;
import com.lucas.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lucas.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lucas.bankingsystem.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
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
        eventPublisher.publishEvent(
                new UserOperationEvent(savedUser.getId(), UserOperationType.CREATED)
        );
        return new UserResponseDTO(
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.isActive()
        );
    }
}
