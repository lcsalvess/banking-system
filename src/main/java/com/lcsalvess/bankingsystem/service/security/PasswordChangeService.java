package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.InvalidCurrentPasswordException;
import com.lcsalvess.bankingsystem.exception.user.PasswordUnchangedException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.ratelimit.PasswordChangeAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimitProtectionService rateLimitProtectionService;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            ApplicationEventPublisher eventPublisher,
            RateLimitProtectionService rateLimitProtectionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
        this.rateLimitProtectionService = rateLimitProtectionService;
    }

    @Transactional
    public void changePassword(ChangePasswordRequestDTO dto) {
        Long userId = currentUserService.getCurrentUser().getId();

        // A tentativa é contabilizada antes de verificar a senha atual, para que
        // requisições paralelas não ultrapassem o limite enquanto o BCrypt roda.
        // O contador fica em memória: não é desfeito se a transação sofrer rollback.
        PasswordChangeAttempt attempt =
                rateLimitProtectionService.beginPasswordChangeAttempt(userId);

        User user;

        try {
            user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new UserNotFoundException(
                            ApiErrorMessages.USER_NOT_FOUND
                    ));

            if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
                throw new InvalidCurrentPasswordException(
                        ApiErrorMessages.INVALID_CURRENT_PASSWORD
                );
            }
        } catch (InvalidCurrentPasswordException exception) {
            // Senha atual incorreta: a tentativa continua contabilizada.
            throw exception;
        } catch (RuntimeException exception) {
            // Falhas de infraestrutura ou inesperadas não são senha incorreta.
            rateLimitProtectionService.cancelPasswordChangeAttempt(attempt);
            throw exception;
        }

        // Senha atual confirmada: o contador deixa de pesar contra o usuário.
        rateLimitProtectionService.recordSuccessfulCurrentPasswordVerification(attempt);

        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new PasswordUnchangedException(
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