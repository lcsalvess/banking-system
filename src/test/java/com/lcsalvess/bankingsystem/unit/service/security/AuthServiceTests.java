package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.security.AuthService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
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

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.service.security.AuthServiceTests#";

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("Ao autenticar um usuário")
    class AuthenticateTests {

        @Test
        @DisplayName("Deve autenticar usuário com credenciais válidas")
        void shouldAuthenticateUserSuccessfully() {
            String jwtToken = "jwt-token";
            LoginRequestDTO dto = createLoginRequestDTO();
            User user = createEntityUser();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenReturn(authentication);

            when(authentication.getPrincipal()).thenReturn(user);
            when(jwtService.generateToken(user)).thenReturn(jwtToken);

            LoginResponseDTO response = authService.authenticate(dto);

            assertEquals(jwtToken, response.token());
            assertEquals(user.getUsername(), response.username());
            assertEquals(user.getEmail(), response.email());
            assertEquals(user.getRole(), response.role());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );
            verify(jwtService).generateToken(user);
        }

        @Test
        @DisplayName("Deve lançar exceção quando as credenciais são inválidas")
        void shouldThrowWhenCredentialsAreInvalid() {
            LoginRequestDTO dto = createLoginRequestDTO();

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(
                    new BadCredentialsException(
                            ApiErrorMessages.INVALID_CREDENTIALS
                    )
            );

            assertThrows(
                    BadCredentialsException.class,
                    () -> authService.authenticate(dto)
            );

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );
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

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            BadCredentialsException exception = assertThrows(
                    BadCredentialsException.class,
                    () -> authService.authenticate(dto)
            );

            assertEquals(
                    ApiErrorMessages.INVALID_CREDENTIALS,
                    exception.getMessage()
            );
            assertSame(original, exception.getCause());

            verify(authManager).authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            );
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName(
                "Não deve mascarar falhas de infraestrutura como credenciais inválidas"
        )
        void shouldNotMaskInfrastructureFailures() {
            LoginRequestDTO dto = createLoginRequestDTO();
            AuthenticationServiceException original =
                    new AuthenticationServiceException("database unavailable");

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            AuthenticationServiceException exception = assertThrows(
                    AuthenticationServiceException.class,
                    () -> authService.authenticate(dto)
            );

            assertSame(original, exception);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("Deve propagar exceções inesperadas sem alterá-las")
        void shouldPropagateUnexpectedExceptions() {
            LoginRequestDTO dto = createLoginRequestDTO();
            IllegalStateException original =
                    new IllegalStateException("unexpected");

            when(authManager.authenticate(
                    any(UsernamePasswordAuthenticationToken.class)
            )).thenThrow(original);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> authService.authenticate(dto)
            );

            assertSame(original, exception);
            verifyNoInteractions(jwtService);
        }

        private LoginRequestDTO createLoginRequestDTO() {
            return new LoginRequestDTO(
                    "usuario.teste",
                    "teste123456"
            );
        }

        private User createEntityUser() {
            return new User(
                    "usuario.teste",
                    "teste@teste.com.br",
                    "encoded-password",
                    Role.EMPLOYEE
            );
        }
    }

    @SuppressWarnings("unused") // usado via @MethodSource
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
                        new AccountExpiredException("User account has expired")
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