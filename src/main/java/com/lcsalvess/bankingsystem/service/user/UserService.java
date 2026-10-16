package com.lcsalvess.bankingsystem.service.user;

import com.lcsalvess.bankingsystem.dto.request.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.UserResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.UserSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.LastAdminCannotBeDeactivatedException;
import com.lcsalvess.bankingsystem.exception.user.UserEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.exception.user.UsernameAlreadyExistsException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.specification.UserSpecifications;
import com.lcsalvess.bankingsystem.util.EmailNormalizer;
import com.lcsalvess.bankingsystem.util.OptionalTextNormalizer;
import com.lcsalvess.bankingsystem.util.PageableTieBreaker;
import com.lcsalvess.bankingsystem.util.UsernameNormalizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public UserResponseDTO deactivate(Long userId) {
        // O lock vem ANTES de carregar o alvo. Assim, as entidades de admin
        // entram no contexto de persistência já com o estado commitado mais
        // recente, e uma desativação concorrente do mesmo usuário é vista como
        // "já desativado" (idempotente) em vez de virar um 409 indevido.
        List<User> activeAdmins = lockActiveAdminsIfTargetIsAdmin(userId);

        User user = findUserOrThrow(userId);

        if (!user.isActive()) {
            return toResponse(user);
        }

        validateNotLastActiveAdmin(user, activeAdmins);

        user.deactivate();

        eventPublisher.publishEvent(
                new UserOperationEvent(
                        user.getId(),
                        UserOperationType.DEACTIVATED
                )
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

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        ApiErrorMessages.USER_NOT_FOUND
                ));
    }

    /**
     * Só administradores precisam do lock. O papel de um usuário não muda após
     * a criação, então a checagem prévia de papel é segura.
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
                user.isActive()
        );
    }
}