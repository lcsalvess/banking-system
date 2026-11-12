package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.exception.security.RateLimitOperation;
import com.lcsalvess.bankingsystem.exception.security.RateLimitScope;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
import com.lcsalvess.bankingsystem.service.security.AuthenticationService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.bankingsystem.service.security.ratelimit.LoginAttempt;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
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
class AuthenticationServiceTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.service.security.AuthenticationServiceTests#";

    private static final String TEST_CLIENT_IP = "192.0.2.1";

    private static final Instant ATTEMPT_TIME =
            Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimitProtectionService rateLimitProtectionService;

    @Mock
    private AuditTrailRecorder auditTrailRecorder;

    private AuthenticationService authService;
    private User testUser;
    private LoginAttempt testAttempt;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString()))
                .thenReturn("dummy-hash");

        authService = new AuthenticationService(
                authManager,
                jwtService,
                passwordEncoder,
                rateLimitProtectionService,
                auditTrailRecorder
        );

        testUser = UserTestFactory.aUser()
                .build();

        testAttempt = new LoginAttempt(
                "username-ip:" + testUser.getUsername() + "|" + TEST_CLIENT_IP,
                ATTEMPT_TIME,
                "username:" + testUser.getUsername(),
                ATTEMPT_TIME
        );
    }

    @Nested
    @DisplayName("When authenticating a user")
    class AuthenticateTests {

        @Test
        @DisplayName("Should authenticate the user with valid credentials")
        void shouldAuthenticateUserSuccessfully() {
            String jwtToken = "jwt-token";
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenReturn(authentication);

            when(authentication.getPrincipal()).thenReturn(testUser);
            when(jwtService.generateToken(testUser)).thenReturn(jwtToken);

            LoginResponseDTO response =
                    authService.authenticate(dto, TEST_CLIENT_IP);

            assertEquals(jwtToken, response.token());
            assertEquals(testUser.getUsername(), response.username());
            assertEquals(testUser.getEmail(), response.email());
            assertEquals(testUser.getRole(), response.role());

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(jwtService).generateToken(testUser);

            verify(rateLimitProtectionService)
                    .recordSuccessfulLogin(testAttempt);

            verify(rateLimitProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));

            verify(auditTrailRecorder)
                    .recordLoginSuccess(testUser, TEST_CLIENT_IP);
        }

        @Test
        @DisplayName("Should throw and record a failure when credentials are invalid")
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

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(rateLimitProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verify(auditTrailRecorder)
                    .recordLoginFailure(
                            testUser.getUsername(),
                            TEST_CLIENT_IP,
                            "INVALID_CREDENTIALS"
                    );

            verifyNoInteractions(jwtService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "accountStatusExceptions")
        @DisplayName(
                "Should treat inactive, locked, or expired accounts as invalid credentials"
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

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );

            verify(passwordEncoder).matches(
                    dto.password(),
                    "dummy-hash"
            );

            verify(rateLimitProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verify(auditTrailRecorder)
                    .recordLoginFailure(
                            testUser.getUsername(),
                            TEST_CLIENT_IP,
                            "ACCOUNT_NOT_ENABLED"
                    );

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Should cancel the attempt when an infrastructure failure occurs")
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

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(rateLimitProtectionService)
                    .cancelAttempt(testAttempt);

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService, auditTrailRecorder);
        }

        @Test
        @DisplayName("Should propagate unexpected exceptions unchanged and cancel the attempt")
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

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(rateLimitProtectionService)
                    .cancelAttempt(testAttempt);

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService, auditTrailRecorder);
        }

        @Test
        @DisplayName("Should cancel the attempt when the authentication principal is null")
        void shouldCancelAttemptWhenAuthenticationPrincipalIsNull() {
            LoginRequestDTO dto = createLoginRequestDTO();

            allowAttempt();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenReturn(authentication);

            when(authentication.getPrincipal()).thenReturn(null);

            AuthenticationServiceException exception = assertThrows(
                    AuthenticationServiceException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertEquals(
                    ApiErrorMessages.AUTHENTICATION_FAILED,
                    exception.getMessage()
            );

            verify(rateLimitProtectionService)
                    .beginAttempt(TEST_CLIENT_IP, dto.username());

            verify(rateLimitProtectionService)
                    .cancelAttempt(testAttempt);

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verifyNoInteractions(jwtService, auditTrailRecorder);
        }
    }

    @Nested
    @DisplayName("When exceeding the login attempt limit")
    class RateLimitTests {

        @ParameterizedTest(name = "Scope: {0}")
        @EnumSource(
                value = RateLimitScope.class,
                mode = EnumSource.Mode.EXCLUDE,
                names = "USER_ID"
        )
        @DisplayName("Should propagate the exception and record the block without checking credentials")
        void shouldRejectBeforeCheckingCredentials(
                RateLimitScope scope
        ) {
            LoginRequestDTO dto = createLoginRequestDTO();

            RateLimitExceededException original =
                    new RateLimitExceededException(
                            60L,
                            RateLimitOperation.LOGIN,
                            scope
                    );

            when(rateLimitProtectionService.beginAttempt(
                    TEST_CLIENT_IP,
                    dto.username()
            )).thenThrow(original);

            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            assertSame(original, exception);

            verifyNoInteractions(authManager, jwtService);

            verify(rateLimitProtectionService, never())
                    .recordSuccessfulLogin(any(LoginAttempt.class));

            verify(rateLimitProtectionService, never())
                    .cancelAttempt(any(LoginAttempt.class));

            verify(auditTrailRecorder)
                    .recordLoginBlocked(
                            testUser.getUsername(),
                            TEST_CLIENT_IP,
                            scope
                    );
        }

        @Test
        @DisplayName("Should not compare the password against the dummy hash when the attempt is rejected")
        void shouldNotRunPasswordCheckWhenRejected() {
            LoginRequestDTO dto = createLoginRequestDTO();

            when(rateLimitProtectionService.beginAttempt(
                    TEST_CLIENT_IP,
                    dto.username()
            )).thenThrow(new RateLimitExceededException(
                    1L,
                    RateLimitOperation.LOGIN,
                    RateLimitScope.CLIENT_IP
            ));

            assertThrows(
                    RateLimitExceededException.class,
                    () -> authService.authenticate(dto, TEST_CLIENT_IP)
            );

            verify(passwordEncoder, never()).matches(any(), any());

            verify(auditTrailRecorder)
                    .recordLoginBlocked(
                            testUser.getUsername(),
                            TEST_CLIENT_IP,
                            RateLimitScope.CLIENT_IP
                    );
        }
    }

    private void allowAttempt() {
        when(rateLimitProtectionService.beginAttempt(
                TEST_CLIENT_IP,
                testUser.getUsername()
        )).thenReturn(testAttempt);
    }

    private LoginRequestDTO createLoginRequestDTO() {
        return new LoginRequestDTO(
                testUser.getUsername(),
                "teste123456"
        );
    }

    @SuppressWarnings("unused") // Used by @MethodSource.
    static Stream<Arguments> accountStatusExceptions() {
        return Stream.of(
                Arguments.of(
                        "Inactive account",
                        new DisabledException("User is disabled")
                ),
                Arguments.of(
                        "Locked account",
                        new LockedException("User account is locked")
                ),
                Arguments.of(
                        "Expired account",
                        new AccountExpiredException(
                                "User account has expired"
                        )
                ),
                Arguments.of(
                        "Expired credentials",
                        new CredentialsExpiredException(
                                "User credentials have expired"
                        )
                )
        );
    }
}
