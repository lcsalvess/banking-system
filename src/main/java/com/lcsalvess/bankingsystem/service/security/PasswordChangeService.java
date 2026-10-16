package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.InvalidCurrentPasswordException;
import com.lcsalvess.bankingsystem.exception.user.PasswordUnchangedException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void changePassword(ChangePasswordRequestDTO dto) {
        Long userId = currentUserService.getCurrentUser().getId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        ApiErrorMessages.USER_NOT_FOUND
                ));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException(
                    ApiErrorMessages.INVALID_CURRENT_PASSWORD
            );
        }

        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw  new PasswordUnchangedException(
                    ApiErrorMessages.PASSWORD_UNCHANGED
            );
        }

        user.changePassword(passwordEncoder.encode(dto.newPassword()));

        eventPublisher.publishEvent(
                new UserOperationEvent(
                        user.getId(),
                        UserOperationType.PASSWORD_CHANGED
                )
        );
    }
}
