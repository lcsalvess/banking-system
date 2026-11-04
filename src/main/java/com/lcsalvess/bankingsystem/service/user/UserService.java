package com.lcsalvess.bankingsystem.service.user;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.event.user.UserRoleChangedEvent;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.SelfDeactivationNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.SelfRoleChangeNotAllowedException;
import com.lcsalvess.bankingsystem.exception.user.SystemUserCannotBeReactivatedException;
import com.lcsalvess.bankingsystem.exception.user.SystemUserRoleCannotBeChangedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.specification.UserSpecifications;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.normalization.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.OptionalTextNormalizer;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UserService {

    private static final String SYSTEM_MIGRATION_USERNAME =
            "system_migration";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponseDTO> findAll(
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
                        PageableTieBreaker.withIdTieBreaker(pageable)
                )
                .map(this::toSummaryResponse);
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO dto) {
        String username = UsernameNormalizer.normalize(dto.username());
        String email = EmailNormalizer.normalize(dto.email());

        validateUsernameAvailability(username);
        validateEmailAvailability(email);

        User user = new User(
                username,
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

        return toResponse(savedUser);
    }

    @Transactional
    public UserResponseDTO reactivate(Long userId) {
        User user = findUserForUpdateOrThrow(userId);

        if (isSystemUser(user)) {
            throw new SystemUserCannotBeReactivatedException(
                    ApiErrorMessages.SYSTEM_USER_CANNOT_BE_REACTIVATED
            );
        }

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

    @Transactional
    public UserResponseDTO deactivate(Long userId) {
        validateNotSelfDeactivation(userId);
        // The lock is acquired BEFORE loading the target. This ensures that
        // administrator entities enter the persistence context with the latest
        // committed state, so a concurrent deactivation of the same user is treated
        // as "already deactivated" (idempotent) rather than resulting in an
        // incorrect 409 response.
        List<User> activeAdmins = lockActiveAdminsIfTargetIsAdmin(userId);

        User user = findUserForUpdateOrThrow(userId);

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

    @Transactional
    public UserResponseDTO changeRole(Long userId, Role newRole) {
        validateNotSelfRoleChange(userId);

        // Active administrators are always locked (without a prior role check,
        // which could become stale before acquiring the lock), using the same
        // order as deactivation: administrators by ID, followed by the target,
        // preventing deadlocks.
        List<User> activeAdmins = userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN);

        User user = findUserForUpdateOrThrow(userId);

        if (isSystemUser(user)) {
            throw new SystemUserRoleCannotBeChangedException(
                    ApiErrorMessages.SYSTEM_USER_ROLE_CANNOT_BE_CHANGED
            );
        }

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

    private User findUserForUpdateOrThrow(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        ApiErrorMessages.USER_NOT_FOUND
                ));
    }

    /**
     * Only administrators require the lock during deactivation. If the target's
     * role changes between this check and locking their row, the subsequent
     * validation fails closed (409), ensuring the system is never left without
     * an administrator.
     * Role changes do not use this check: they always lock all administrators.
     */
    private List<User> lockActiveAdminsIfTargetIsAdmin(Long userId) {
        if (!userRepository.existsByIdAndRole(userId, Role.ADMIN)) {
            return List.of();
        }

        return userRepository.findActiveUsersByRoleForUpdate(Role.ADMIN);
    }

    private void validateNotLastActiveAdmin(User user, List<User> activeAdmins) {
        if (user.getRole() != Role.ADMIN) {
            return;
        }

        if (activeAdmins.size() <= 1) {
            throw new LastAdminCannotBeDeactivatedException(
                    ApiErrorMessages.LAST_ADMIN_CANNOT_BE_DEACTIVATED
            );
        }
    }

    private void validateNotSelfDeactivation(Long userId) {
        currentUserService.findCurrentUser()
                .filter(currentUser -> currentUser.getRole() == Role.ADMIN)
                .filter(currentUser -> currentUser.getId() != null
                        && currentUser.getId().equals(userId))
                .ifPresent(currentUser -> {
                    throw new SelfDeactivationNotAllowedException(
                            ApiErrorMessages.SELF_DEACTIVATION_NOT_ALLOWED
                    );
                });
    }

    private void validateNotSelfRoleChange(Long userId) {
        currentUserService.findCurrentUser()
                .filter(currentUser -> currentUser.getId() != null
                        && currentUser.getId().equals(userId))
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
            throw new AccessDeniedException(ApiErrorMessages.ACCESS_DENIED_RESOURCE);
        }
    }

    private static boolean isSystemUser(User user) {
        return SYSTEM_MIGRATION_USERNAME.equals(
                UsernameNormalizer.normalize(user.getUsername())
        );
    }

    private UserSummaryResponseDTO toSummaryResponse(User user) {
        return new UserSummaryResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive()
        );
    }

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
