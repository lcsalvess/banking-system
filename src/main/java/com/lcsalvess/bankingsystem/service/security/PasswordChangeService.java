package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDto;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles authenticated user password change operations.
 *
 * <p>Verifies the current password, enforces password change attempt limits,
 * updates the encoded password, and invalidates previously issued JWTs.
 *
 * <p>Publishes a user operation event after a successful password change.
 */
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

  /**
   * Changes the password of the currently authenticated user.
   *
   * <p>Applies rate limiting to password verification attempts, validates
   * the current password, and rejects requests that reuse the existing password.
   *
   * <p>Successful password changes increment the user's token version,
   * invalidating previously issued JWTs.
   *
   * @param dto the request containing the current and new passwords
   * @throws InvalidCurrentPasswordException if the current password is incorrect
   * @throws PasswordUnchangedException if the new password matches the current password
   * @throws UserNotFoundException if the authenticated user cannot be found
   */
  @Transactional
  public void changePassword(ChangePasswordRequestDto dto) {
    Long userId = currentUserService.getCurrentUser().getId();

    // The attempt is counted before verifying the current password so that
    // concurrent requests cannot exceed the limit while BCrypt is running.
    // The counter is kept in memory and is not rolled back if the transaction is rolled back.
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
      // An incorrect current password: the attempt remains counted.
      throw exception;
    } catch (RuntimeException exception) {
      // Infrastructure or unexpected failures are not treated as incorrect passwords.
      rateLimitProtectionService.cancelPasswordChangeAttempt(attempt);
      throw exception;
    }

    // Current password verified: the attempt is no longer counted against the user.
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
