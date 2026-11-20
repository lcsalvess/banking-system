package com.lcsalvess.bankingsystem.service.user;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDto;
import com.lcsalvess.bankingsystem.dto.response.user.CurrentUserResponseDto;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDto;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDto;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.event.user.UserRoleChangedEvent;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.SelfDeactivationNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.SelfRoleChangeNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.specification.UserSpecifications;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.user.generator.EmployeeIdGenerator;
import com.lcsalvess.bankingsystem.util.normalization.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.OptionalTextNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides application services for user management operations.
 *
 * <p>Handles user creation, retrieval, activation, deactivation, and role changes,
 * including validation, concurrency control, and publication of user operation events.
 */
@Service
public class UserService {

  private final EmployeeIdGenerator employeeIdGenerator;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final CurrentUserService currentUserService;

  public UserService(
      EmployeeIdGenerator employeeIdGenerator,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher eventPublisher,
      CurrentUserService currentUserService
  ) {
    this.employeeIdGenerator = employeeIdGenerator;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.currentUserService = currentUserService;
  }

  /**
   * Retrieves users matching the specified filters using pagination.
   *
   * <p>Supports filtering by search term, role, and activation status.
   * Results use the employee identifier as a pagination tie-breaker.
   *
   * @param search the optional search term
   * @param role the optional user role filter
   * @param active the optional account activation status filter
   * @param pageable the pagination and sorting configuration
   * @return a page of user summary responses matching the filters
   */
  @Transactional(readOnly = true)
  public Page<UserSummaryResponseDto> findAll(
      String search,
      Role role,
      Boolean active,
      Pageable pageable
  ) {
    String normalizedSearch = OptionalTextNormalizer.normalize(search);

    return userRepository.findAll(
            UserSpecifications.byFilters(
                normalizedSearch,
                role,
                active
            ),
            PageableTieBreaker.withTieBreaker(pageable, "employeeId")
        )
        .map(this::toSummaryResponse);
  }

  /**
   * Retrieves the profile of the currently authenticated user.
   *
   * @return the response containing the authenticated user's profile
   */
  public CurrentUserResponseDto getCurrentUserProfile() {
    User user = currentUserService.getCurrentUser();

    return new CurrentUserResponseDto(
        user.getEmployeeId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRole()
    );
  }

  /**
   * Creates a new user account.
   *
   * <p>Normalizes the username and email, validates their availability,
   * encodes the password, generates an employee identifier, and publishes
   * a user creation event after persistence.
   *
   * @param dto the user creation data
   * @return the response containing the created user's information
   * @throws UsernameAlreadyExistsException if the username is already registered
   * @throws UserEmailAlreadyExistsException if the email address is already registered
   */
  @Transactional
  public UserResponseDto create(UserRequestDto dto) {
    String username = UsernameNormalizer.normalize(dto.username());
    String email = EmailNormalizer.normalize(dto.email());

    validateUsernameAvailability(username);
    validateEmailAvailability(email);

    String rawPassword = Objects.requireNonNull(
        dto.password(),
        "Password is required."
    );

    String encodedPassword = Objects.requireNonNull(
        passwordEncoder.encode(rawPassword),
        "Password encoder returned a null encoded password."
    );

    User user = User.createHuman(
        employeeIdGenerator.generate(),
        dto.fullName(),
        username,
        email,
        encodedPassword,
        dto.role()
    );

    User savedUser = userRepository.save(user);

    eventPublisher.publishEvent(
        new UserOperationEvent(
            savedUser.getId(),
            UserOperationType.CREATED
        )
    );

    return toResponse(savedUser);
  }

  /**
   * Reactivates an existing user account.
   *
   * <p>If the user is already active, returns the current user information
   * without performing another state transition.
   *
   * @param employeeId the employee identifier of the user to reactivate
   * @return the response containing the user's updated information
   * @throws UserNotFoundException if no user exists with the specified employee identifier
   */
  @Transactional
  public UserResponseDto reactivate(String employeeId) {
    User user = findUserForUpdateOrThrow(employeeId);

    if (user.isActive()) {
      return toResponse(user);
    }

    user.reactivate();

    userRepository.flush();

    eventPublisher.publishEvent(
        new UserOperationEvent(
            user.getId(),
            UserOperationType.REACTIVATED
        )
    );

    return toResponse(user);
  }

  /**
   * Deactivates an existing user account.
   *
   * <p>Prevents self-deactivation and ensures that at least one active
   * administrator remains in the system.
   *
   * <p>If the user is already inactive, returns the current user information
   * without performing another state transition.
   *
   * @param employeeId the employee identifier of the user to deactivate
   * @return the response containing the user's updated information
   * @throws SelfDeactivationNotAllowedException if an administrator attempts
   *     to deactivate themselves
   * @throws LastAdminCannotBeDeactivatedException if the operation would
   *     deactivate the last active administrator
   * @throws UserNotFoundException if no user exists with the specified
   *     employee identifier
   */
  @Transactional
  public UserResponseDto deactivate(String employeeId) {
    validateNotSelfDeactivation(employeeId);

    // The lock is acquired BEFORE loading the target. This ensures that
    // administrator entities enter the persistence context with the latest
    // committed state, so a concurrent deactivation of the same user is treated
    // as "already deactivated" (idempotent) rather than resulting in an
    // incorrect 409 response.
    List<User> activeAdmins =
        lockActiveAdminsIfTargetIsAdmin(employeeId);

    User user = findUserForUpdateOrThrow(employeeId);

    if (!user.isActive()) {
      return toResponse(user);
    }

    validateNotLastActiveAdmin(user, activeAdmins);

    user.deactivate();

    userRepository.flush();

    eventPublisher.publishEvent(
        new UserOperationEvent(
            user.getId(),
            UserOperationType.DEACTIVATED
        )
    );

    return toResponse(user);
  }

  /**
   * Changes the role assigned to an existing user.
   *
   * <p>Prevents users from changing their own roles and verifies that the
   * requesting administrator remains active. Uses pessimistic locking
   * to protect concurrent role changes and administrator deactivation.
   *
   * @param employeeId the employee identifier of the user whose role will change
   * @param newRole the new role to assign
   * @return the response containing the user's updated information
   * @throws SelfRoleChangeNotAllowedException if the current user attempts to change their own role
   * @throws UserNotFoundException if no user exists with the specified employee identifier
   * @throws AccessDeniedException if the requesting user is not an active administrator
   */
  @Transactional
  public UserResponseDto changeRole(String employeeId, Role newRole) {
    validateNotSelfRoleChange(employeeId);

    // Active administrators are always locked (without a prior role check,
    // which could become stale before acquiring the lock), using the same
    // order as deactivation: administrators by ID, followed by the target,
    // preventing deadlocks.
    List<User> activeAdmins =
        userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN);

    User user = findUserForUpdateOrThrow(employeeId);

    // The caller must remain an active administrator after acquiring the lock.
    // Since the target can never be the caller, at least one active administrator
    // always remains, even if two administrators attempt to demote each other concurrently.
    validateCallerIsActiveAdmin(activeAdmins);

    if (user.getRole() == newRole) {
      return toResponse(user);
    }

    Role previousRole = user.getRole();

    user.changeRole(newRole);

    userRepository.flush();

    eventPublisher.publishEvent(
        new UserRoleChangedEvent(user.getId(), previousRole, newRole)
    );

    return toResponse(user);
  }

  private void validateUsernameAvailability(String username) {
    if (userRepository.existsByUsernameIgnoreCase(username)) {
      throw new UsernameAlreadyExistsException(
          ApiErrorMessages.USERNAME_ALREADY_EXISTS
      );
    }
  }

  private void validateEmailAvailability(String email) {
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new UserEmailAlreadyExistsException(
          ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS
      );
    }
  }

  private User findUserForUpdateOrThrow(String employeeId) {
    return userRepository.findByEmployeeIdForUpdate(employeeId)
        .orElseThrow(() -> new UserNotFoundException(
            ApiErrorMessages.USER_NOT_FOUND
        ));
  }

  /**
   * Only administrators require the lock during deactivation. If the target's
   * role changes between this check and locking their row, the subsequent
   * validation fails closed (409), ensuring the system is never left without
   * an administrator.
   *
   * <p>Role changes do not use this check: they always lock all administrators.
   */
  private List<User> lockActiveAdminsIfTargetIsAdmin(String employeeId) {
    if (!userRepository.existsByEmployeeIdAndRole(
        employeeId,
        Role.ADMIN
    )) {
      return List.of();
    }

    return userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN);
  }

  private void validateNotLastActiveAdmin(
      User user,
      List<User> activeAdmins
  ) {
    if (user.getRole() != Role.ADMIN) {
      return;
    }

    if (activeAdmins.size() <= 1) {
      throw new LastAdminCannotBeDeactivatedException(
          ApiErrorMessages.LAST_ADMIN_CANNOT_BE_DEACTIVATED
      );
    }
  }

  private void validateNotSelfDeactivation(String employeeId) {
    currentUserService.findCurrentUser()
        .filter(currentUser -> currentUser.getRole() == Role.ADMIN)
        .filter(currentUser ->
            Objects.equals(
                currentUser.getEmployeeId(),
                employeeId
            )
        )
        .ifPresent(currentUser -> {
          throw new SelfDeactivationNotAllowedException(
              ApiErrorMessages.SELF_DEACTIVATION_NOT_ALLOWED
          );
        });
  }

  private void validateNotSelfRoleChange(String employeeId) {
    currentUserService.findCurrentUser()
        .filter(currentUser ->
            Objects.equals(
                currentUser.getEmployeeId(),
                employeeId
            )
        )
        .ifPresent(currentUser -> {
          throw new SelfRoleChangeNotAllowedException(
              ApiErrorMessages.SELF_ROLE_CHANGE_NOT_ALLOWED
          );
        });
  }

  private void validateCallerIsActiveAdmin(List<User> activeAdmins) {
    Long callerId = currentUserService.getCurrentUser().getId();

    boolean callerIsActiveAdmin = activeAdmins.stream()
        .anyMatch(admin -> Objects.equals(admin.getId(), callerId));

    if (!callerIsActiveAdmin) {
      throw new AccessDeniedException(
          ApiErrorMessages.ACCESS_DENIED_RESOURCE
      );
    }
  }

  private UserSummaryResponseDto toSummaryResponse(User user) {
    return new UserSummaryResponseDto(
        user.getEmployeeId(),
        user.getFullName(),
        user.getUsername(),
        user.getEmail(),
        user.getRole(),
        user.isActive()
    );
  }

  private UserResponseDto toResponse(User user) {
    return new UserResponseDto(
        user.getEmployeeId(),
        user.getFullName(),
        user.getUsername(),
        user.getEmail(),
        user.getRole(),
        user.isActive(),
        user.getCreatedAt(),
        user.getUpdatedAt()
    );
  }
}
