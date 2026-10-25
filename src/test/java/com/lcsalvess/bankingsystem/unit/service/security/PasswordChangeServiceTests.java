package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.exception.user.InvalidCurrentPasswordException;
import com.lcsalvess.bankingsystem.exception.user.PasswordUnchangedException;
import com.lcsalvess.bankingsystem.exception.user.UserNotFoundException;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.security.ratelimit.PasswordChangeAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import com.lcsalvess.bankingsystem.service.security.PasswordChangeService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private RateLimitProtectionService rateLimitProtectionService;

    private PasswordChangeService passwordChangeService;
    private PasswordChangeAttempt attempt;

    private User principal;
    private User persistedUser;

    @BeforeEach
    void setUp() {
        passwordChangeService = new PasswordChangeService(
                userRepository,
                passwordEncoder,
                currentUserService,
                eventPublisher,
                rateLimitProtectionService
        );

        attempt = new PasswordChangeAttempt(
                "password-change:" + USER_ID,
                Instant.parse("2026-01-01T12:00:00Z")
        );

        lenient().when(rateLimitProtectionService.beginPasswordChangeAttempt(USER_ID))
                .thenReturn(attempt);

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

    @Nested
    @DisplayName("Ao contabilizar tentativas de troca de senha")
    class AttemptTrackingTests {

        @Test
        @DisplayName("Deve zerar o contador quando a senha atual é confirmada")
        void shouldRecordSuccessWhenCurrentPasswordIsConfirmed() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
            when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH)).thenReturn(false);
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

            passwordChangeService.changePassword(request(CURRENT_PASSWORD, NEW_PASSWORD));

            verify(rateLimitProtectionService).beginPasswordChangeAttempt(USER_ID);
            verify(rateLimitProtectionService).recordSuccessfulCurrentPasswordVerification(attempt);
            verify(rateLimitProtectionService, never())
                    .cancelPasswordChangeAttempt(attempt);
        }

        @Test
        @DisplayName("Deve zerar o contador mesmo quando a nova senha é igual à atual")
        void shouldRecordSuccessWhenNewPasswordEqualsCurrent() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);

            assertThrows(
                    PasswordUnchangedException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, CURRENT_PASSWORD)
                    )
            );

            verify(rateLimitProtectionService).recordSuccessfulCurrentPasswordVerification(attempt);
            verify(rateLimitProtectionService, never())
                    .cancelPasswordChangeAttempt(attempt);
        }

        @Test
        @DisplayName("Deve manter a tentativa contabilizada quando a senha atual está incorreta")
        void shouldKeepAttemptCountedWhenCurrentPasswordIsIncorrect() {
            mockCurrentUserFound();
            when(passwordEncoder.matches("wrong-password", CURRENT_HASH)).thenReturn(false);

            assertThrows(
                    InvalidCurrentPasswordException.class,
                    () -> passwordChangeService.changePassword(
                            request("wrong-password", NEW_PASSWORD)
                    )
            );

            verify(rateLimitProtectionService).beginPasswordChangeAttempt(USER_ID);
            verify(rateLimitProtectionService, never())
                    .recordSuccessfulCurrentPasswordVerification(attempt);
            verify(rateLimitProtectionService, never())
                    .cancelPasswordChangeAttempt(attempt);
        }

        @Test
        @DisplayName("Deve desfazer a contagem quando o usuário autenticado não existe mais")
        void shouldCancelAttemptWhenUserNoLongerExists() {
            when(currentUserService.getCurrentUser()).thenReturn(principal);
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

            assertThrows(
                    UserNotFoundException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, NEW_PASSWORD)
                    )
            );

            verify(rateLimitProtectionService).cancelPasswordChangeAttempt(attempt);
            verify(rateLimitProtectionService, never())
                    .recordSuccessfulCurrentPasswordVerification(attempt);
        }

        @Test
        @DisplayName("Deve desfazer a contagem quando ocorre falha de infraestrutura")
        void shouldCancelAttemptWhenInfrastructureFails() {
            when(currentUserService.getCurrentUser()).thenReturn(principal);
            when(userRepository.findByIdForUpdate(USER_ID))
                    .thenThrow(new DataAccessResourceFailureException("database down"));

            assertThrows(
                    DataAccessResourceFailureException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, NEW_PASSWORD)
                    )
            );

            verify(rateLimitProtectionService).cancelPasswordChangeAttempt(attempt);
            verify(rateLimitProtectionService, never())
                    .recordSuccessfulCurrentPasswordVerification(attempt);
            verifyNoInteractions(passwordEncoder, eventPublisher);
        }

        @Test
        @DisplayName("Deve propagar o bloqueio sem consultar o usuário nem executar o BCrypt")
        void shouldPropagateRateLimitWithoutCheckingPassword() {
            when(currentUserService.getCurrentUser()).thenReturn(principal);
            when(rateLimitProtectionService.beginPasswordChangeAttempt(USER_ID))
                    .thenThrow(new RateLimitExceededException(
                            60L,
                            RateLimitOperation.PASSWORD_CHANGE,
                            RateLimitScope.USER_ID
                    ));

            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, NEW_PASSWORD)
                    )
            );

            assertEquals(RateLimitOperation.PASSWORD_CHANGE, exception.getOperation());
            assertEquals(RateLimitScope.USER_ID, exception.getScope());

            verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
        }
    }

    @Nested
    @DisplayName("Ao limitar tentativas com o limitador real")
    class RealRateLimiterTests {

        private static final int MAX_FAILED_ATTEMPTS = 3;
        private static final String WRONG_PASSWORD = "wrong-password";

        @BeforeEach
        void useRealRateLimiter() {
            LoginRateLimitProperties properties = new LoginRateLimitProperties(
                    100,
                    Duration.ofMinutes(1),
                    MAX_FAILED_ATTEMPTS,
                    MAX_FAILED_ATTEMPTS * 2,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(5),
                    1000
            );

            RateLimitProtectionService realRateLimiter =
                    new RateLimitProtectionService(
                            properties,
                            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
                            new SimpleMeterRegistry()
                    );

            passwordChangeService = new PasswordChangeService(
                    userRepository,
                    passwordEncoder,
                    currentUserService,
                    eventPublisher,
                    realRateLimiter
            );
        }

        @Test
        @DisplayName("Deve bloquear após o limite de senhas atuais incorretas, sem executar o BCrypt")
        void shouldBlockAfterMaxWrongCurrentPasswords() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(WRONG_PASSWORD, CURRENT_HASH)).thenReturn(false);

            for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
                assertThrows(
                        InvalidCurrentPasswordException.class,
                        () -> passwordChangeService.changePassword(
                                request(WRONG_PASSWORD, NEW_PASSWORD)
                        )
                );
            }

            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> passwordChangeService.changePassword(
                            request(WRONG_PASSWORD, NEW_PASSWORD)
                    )
            );

            assertEquals(RateLimitOperation.PASSWORD_CHANGE, exception.getOperation());
            assertEquals(RateLimitScope.USER_ID, exception.getScope());

            verify(passwordEncoder, times(MAX_FAILED_ATTEMPTS))
                    .matches(WRONG_PASSWORD, CURRENT_HASH);
            verify(userRepository, times(MAX_FAILED_ATTEMPTS)).findByIdForUpdate(USER_ID);
        }

        @Test
        @DisplayName("Não deve bloquear quando a senha atual correta zera o contador")
        void shouldNotBlockWhenCorrectCurrentPasswordResetsCounter() {
            mockCurrentUserFound();
            when(passwordEncoder.matches(WRONG_PASSWORD, CURRENT_HASH)).thenReturn(false);
            when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);

            for (int i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
                assertThrows(
                        InvalidCurrentPasswordException.class,
                        () -> passwordChangeService.changePassword(
                                request(WRONG_PASSWORD, NEW_PASSWORD)
                        )
                );
            }

            // Confirma a senha atual (a nova é igual, então a troca é recusada),
            // o que zera o contador.
            assertThrows(
                    PasswordUnchangedException.class,
                    () -> passwordChangeService.changePassword(
                            request(CURRENT_PASSWORD, CURRENT_PASSWORD)
                    )
            );

            // Sem o reset, esta seria a 4ª tentativa e seria rejeitada com 429.
            assertThrows(
                    InvalidCurrentPasswordException.class,
                    () -> passwordChangeService.changePassword(
                            request(WRONG_PASSWORD, NEW_PASSWORD)
                    )
            );
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
