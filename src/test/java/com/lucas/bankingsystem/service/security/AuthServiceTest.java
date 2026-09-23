package com.lucas.bankingsystem.service.security;

import com.lucas.bankingsystem.dto.request.security.LoginRequestDTO;
import com.lucas.bankingsystem.dto.response.security.LoginResponseDTO;
import com.lucas.bankingsystem.entity.User;
import com.lucas.bankingsystem.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
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
            when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal())
                    .thenReturn(user);
            when(jwtService.generateToken(user))
                    .thenReturn(jwtToken);
            LoginResponseDTO response = authService.authenticate(dto);
            assertEquals(jwtToken, response.token());
            assertEquals(user.getUsername(), response.username());
            assertEquals(user.getEmail(), response.email());
            assertEquals(user.getRole(), response.role());
            verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(jwtService).generateToken(user);
        }

        @Test
        @DisplayName("Deve lançar exceção quando as credenciais são inválidas")
        void shouldThrowWhenCredentialsAreInvalid() {
            LoginRequestDTO dto = createLoginRequestDTO();
            when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Credenciais inválidas."));
            assertThrows(BadCredentialsException.class, () -> authService.authenticate(dto));
            verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verifyNoInteractions(jwtService);
        }

        private LoginRequestDTO createLoginRequestDTO() {
            return new LoginRequestDTO("usuario.teste", "teste123456");
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
}
