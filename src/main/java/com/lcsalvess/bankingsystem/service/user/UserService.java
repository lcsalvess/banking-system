package com.lcsalvess.bankingsystem.service.user;

import com.lcsalvess.bankingsystem.dto.request.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.UserResponseDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.util.EmailNormalizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO dto) {
        String email = EmailNormalizer.normalize(dto.email());

        if (userRepository.existsByUsername(dto.username())) {
            throw new UsernameAlreadyExistsException(
                    ApiErrorMessages.USERNAME_ALREADY_EXISTS
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new UserEmailAlreadyExistsException(
                    ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS
            );
        }

        User user = new User(
                dto.username(),
                email,
                passwordEncoder.encode(dto.password()),
                dto.role()
        );

        User savedUser = userRepository.save(user);

        eventPublisher.publishEvent(
                new UserOperationEvent(
                        savedUser.getId(),
                        UserOperationType.CREATED
                )
        );

        return new UserResponseDTO(
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.isActive()
        );
    }
}