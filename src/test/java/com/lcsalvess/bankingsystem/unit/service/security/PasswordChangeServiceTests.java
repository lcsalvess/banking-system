package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.user.InvalidCurrentPasswordException;
import com.lcsalvess.bankingsystem.exception.user.PasswordUnchangedException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.security.PasswordChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTests {

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD = "current-password";
    private static final String NEW_PASSWORD = "new-password";
    private static final String CURRENT_HASH = "current-hash";
    private static final String NEW_HASH = "new-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PasswordChangeService passwordChangeService;

    private User principal;
    private User persistedUser;

    @BeforeEach
    void setUp() {
        passwordChangeService = new PasswordChangeService(
                userRepository,
                passwordEncoder,
                currentUserService,
                eventPublisher
        );

        principal = createUser("stale-hash");
        persistedUser = createUser(CURRENT_HASH);
    }

    @Nested
    @DisplayName("Ao trocar a senha")
    class ChangePasswordTests {

        @Test
        @DisplayName("Deve trocar a senha, subir a versão das credenciais e publicar o evento")
        void shouldChangePassword() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH)).thenReturn(false);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

            passwordChangeService.changePassword(request(CURRENT_PASSWORD, NEW_PASSWORD));

            assertEquals(NEW_HASH, persistedUser.getPassword());
            assertEquals(1, persistedUser.getTokenVersion());

            verify(eventPublisher).publishEvent(
                    new UserOperationEvent(USER_ID, UserOperationType.PASSWORD_CHANGED)
            );
        }

        @Test
        @DisplayName("Deve conferir a senha atual contra o hash do banco, e não contra o principal da requisição")
        void shouldCheckCurrentPasswordAgainstPersistedHash() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH)).thenReturn(false);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

            passwordChangeService.changePassword(request(CURRENT_PASSWORD, NEW_PASSWORD));

            verify(passwordEncoder, never()).matches(CURRENT_PASSWORD, "stale-hash");
        }

        @Test
        @DisplayName("Deve rejeitar quando a senha atual está incorreta")
        void shouldRejectWhenCurrentPasswordIsIncorrect() {
            mockCurrentUserFound();
            when(passwordEncoder.matches("wrong-password", CURRENT_HASH)).thenReturn(false);

            InvalidCurrentPasswordException exception = assertThrows(
                    InvalidCurrentPasswordException.class,
                    () -> passwordChangeService.changePassword(
                            request("wrong-password", NEW_PASSWORD)
                    )
            );

            assertEquals(ApiErrorMessages.INVALID_CURRENT_PASSWORD, exception.getMessage());
            assertEquals(CURRENT_HASH, persistedUser.getPassword());
            assertEquals(0, persistedUser.getTokenVersion());

            verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve rejeitar quando a nova senha é igual à atual")
        void shouldRejectWhenNewPasswordEqualsCurrent() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);

            PasswordUnchangedException exception = assertThrows(
                    PasswordUnchangedException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, CURRENT_PASSWORD)
                    )
            );

            assertEquals(ApiErrorMessages.PASSWORD_UNCHANGED, exception.getMessage());
            assertEquals(CURRENT_HASH, persistedUser.getPassword());
            assertEquals(0, persistedUser.getTokenVersion());

            verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve lançar exceção quando o usuário autenticado não existe mais")
        void shouldThrowWhenAuthenticatedUserNoLongerExists() {
            when(currentUserService.getCurrentUser()).thenReturn(principal);
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, NEW_PASSWORD)
                    )
            );

            assertEquals(ApiErrorMessages.USER_NOT_FOUND, exception.getMessage());

            verifyNoInteractions(passwordEncoder, eventPublisher);
        }
    }

    private void mockCurrentUserFound() {
        when(currentUserService.getCurrentUser()).thenReturn(principal);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(persistedUser));
    }

    private ChangePasswordRequestDTO request(String currentPassword, String newPassword) {
        return new ChangePasswordRequestDTO(currentPassword, newPassword);
    }

    private User createUser(String passwordHash) {
        User user = new User(
                "usuario.teste",
                "usuario@teste.com",
                passwordHash,
                Role.EMPLOYEE
        );

        ReflectionTestUtils.setField(user, "id", USER_ID);

        return user;
    }
}
