package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.LoginRateLimitReason;
import com.lcsalvess.bankingsystem.service.security.AuthService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.bankingsystem.service.security.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.LoginAttemptProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.service.security.AuthServiceTests#";

    private static final String TEST_CLIENT_IP = "192.0.2.1";
    private static final String TEST_USERNAME = "test.user";

    private static final LoginAttempt TEST_ATTEMPT = new LoginAttempt(
            "username-ip:test.user|192.0.2.1",
            Instant.parse("2026-01-01T12:00:00Z"),
            "username:test.user",
            Instant.parse("2026-01-01T12:00:00Z")
    );

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptProtectionService loginAttemptProtectionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString()))
                .thenReturn("dummy-hash");

        authService = new AuthService(
                authManager,
                jwtService,
                passwordEncoder,
                loginAttemptProtectionService
        );
    }

    @Nested
    @DisplayName("Ao autenticar um usuário")
    class AuthenticateTests {

        @Test
        @DisplayName("Deve autenticar usuário com credenciais válidas")
        void shouldAuthenticateUserSuccessfully() {
            String jwtToken = "jwt-token";
            LoginRequestDTO dto = createLoginRequestDTO();
            User user = createEntityUser();

            allowAttempt();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenReturn(authentication);

            when(authentication.getPrincipal()).thenReturn(user);
            when(jwtService.generateToken(user)).thenReturn(jwtToken);

            LoginResponseDTO response =
                    authService.authenticate(dto, TEST_CLIENT_IP);

            assertEquals(jwtToken, response.token());
            assertEquals(user.getUsername(), response.username());
            assertEquals(user.getEmail(), response.email());
            assertEquals(user.getRole(), response.role());

            verify(loginAttemptProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(jwtService).generateToken(user);

            verify(loginAttemptProtectionService)
                    .recordSuccessfulLogin(TEST_ATTEMPT);
            verify(loginAttemptProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));
        }

        @Test
        @DisplayName("Deve lançar exceção e manter a tentativa contabilizada quando as credenciais são inválidas")
        void shouldThrowWhenCredentialsAreInvalid() {
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(
                    new BadCredentialsException(
                            ApiErrorMessages.INVALID_CREDENTIALS
                    )
            );

            assertThrows(
                    BadCredentialsException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            verify(loginAttemptProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(loginAttemptProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));
            verify(loginAttemptProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "accountStatusExceptions")
        @DisplayName(
                "Deve tratar conta inativa, bloqueada ou expirada como credenciais inválidas"
        )
        void shouldTreatAccountStatusFailuresAsInvalidCredentials(
                String scenario,
                AuthenticationException original
        ) {
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            BadCredentialsException exception = assertThrows(
                    BadCredentialsException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertEquals(
                    ApiErrorMessages.INVALID_CREDENTIALS,
                    exception.getMessage()
            );

            assertSame(original, exception.getCause());

            verify(loginAttemptProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(passwordEncoder).matches(
                    dto.password(),
                    "dummy-hash"
            );

            verify(loginAttemptProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));
            verify(loginAttemptProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName(
                "Deve desfazer a contagem quando ocorre falha de infraestrutura"
        )
        void shouldCancelAttemptOnInfrastructureFailure() {
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            AuthenticationServiceException original =
                    new AuthenticationServiceException(
                            "database unavailable"
                    );

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            AuthenticationServiceException exception = assertThrows(
                    AuthenticationServiceException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertSame(original, exception);

            verify(loginAttemptProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());
            verify(loginAttemptProtectionService)
                    .cancelAttempt(TEST_ATTEMPT);
            verify(loginAttemptProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Deve propagar exceções inesperadas sem alterá-las e desfazer a contagem")
        void shouldPropagateUnexpectedExceptionsAndCancelAttempt() {
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            IllegalStateException original =
                    new IllegalStateException("unexpected");

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertSame(original, exception);

            verify(loginAttemptProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());
            verify(loginAttemptProtectionService)
                    .cancelAttempt(TEST_ATTEMPT);
            verify(loginAttemptProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    @DisplayName("Ao exceder o limite de tentativas de login")
    class RateLimitTests {

        @ParameterizedTest(name = "motivo: {0}")
        @EnumSource(LoginRateLimitReason.class)
        @DisplayName("Deve propagar a exceção sem verificar credenciais nem emitir token")
        void shouldRejectBeforeCheckingCredentials(
                LoginRateLimitReason reason
        ) {
            LoginRequestDTO dto = createLoginRequestDTO();

            LoginRateLimitExceededException original =
                    new LoginRateLimitExceededException(60L, reason);

            when(loginAttemptProtectionService.beginAttempt(
                    TEST_CLIENT_IP,
                    dto.username()
            )).thenThrow(original);

            LoginRateLimitExceededException exception = assertThrows(
                    LoginRateLimitExceededException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertSame(original, exception);

            verifyNoInteractions(authManager, jwtService);

            verify(loginAttemptProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));
            verify(loginAttemptProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));
        }

        @Test
        @DisplayName("Não deve comparar a senha com o hash fictício ao rejeitar")
        void shouldNotRunPasswordCheckWhenRejected() {
            LoginRequestDTO dto = createLoginRequestDTO();

            when(loginAttemptProtectionService.beginAttempt(
                    TEST_CLIENT_IP,
                    dto.username()
            )).thenThrow(new LoginRateLimitExceededException(
                    1L,
                    LoginRateLimitReason.CLIENT_IP
            ));

            assertThrows(
                    LoginRateLimitExceededException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    private void allowAttempt() {
        when(loginAttemptProtectionService.beginAttempt(
                TEST_CLIENT_IP,
                TEST_USERNAME
        )).thenReturn(TEST_ATTEMPT);
    }

    private static LoginRequestDTO createLoginRequestDTO() {
        return new LoginRequestDTO(
                TEST_USERNAME,
                "teste123456"
        );
    }

    private static User createEntityUser() {
        return new User(
                TEST_USERNAME,
                "teste@teste.com.br",
                "encoded-password",
                Role.EMPLOYEE
        );
    }

    @SuppressWarnings("unused") // Usado via @MethodSource.
    static Stream<Arguments> accountStatusExceptions() {
        return Stream.of(
                Arguments.of(
                        "conta inativa",
                        new DisabledException("User is disabled")
                ),
                Arguments.of(
                        "conta bloqueada",
                        new LockedException("User account is locked")
                ),
                Arguments.of(
                        "conta expirada",
                        new AccountExpiredException(
                                "User account has expired"
                        )
                ),
                Arguments.of(
                        "credenciais expiradas",
                        new CredentialsExpiredException(
                                "User credentials have expired"
                        )
                )
        );
    }
}
