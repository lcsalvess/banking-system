package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTests {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder()
                .encodeToString("uma-chave-secreta-com-pelo-menos-32-bytes".getBytes());

        jwtService = new JwtService(secret);
        user = createEntityUser();
    }

    @Nested
    @DisplayName("Ao gerar um token JWT")
    class GenerateTokenTests {

        @Test
        @DisplayName("Deve gerar token para usuário")
        void shouldGenerateTokenSuccessfully() {
            String token = jwtService.generateToken(user);

            assertNotNull(token);
        }
    }

    @Nested
    @DisplayName("Ao extrair o username de um token JWT")
    class ExtractUsernameTests {

        @Test
        @DisplayName("Deve extrair username do token")
        void shouldExtractUsernameSuccessfully() {
            String token = jwtService.generateToken(user);

            String username = jwtService.extractUsername(token);

            assertEquals(user.getUsername(), username);
        }
    }

    @Nested
    @DisplayName("Ao inicializar o serviço JWT")
    class InitializationTests {

        @Test
        @DisplayName("Deve rejeitar segredo nulo")
        void shouldRejectNullSecret() {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtService(null)
            );

            assertEquals(
                    "O segredo JWT não pode ser nulo ou vazio.",
                    exception.getMessage()
            );
        }

        @Test
        @DisplayName("Deve rejeitar segredo vazio")
        void shouldRejectBlankSecret() {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtService(" ")
            );

            assertEquals(
                    "O segredo JWT não pode ser nulo ou vazio.",
                    exception.getMessage()
            );
        }

        @Test
        @DisplayName("Deve rejeitar segredo com Base64 inválido")
        void shouldRejectInvalidBase64Secret() {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtService("!!!")
            );

            assertEquals(
                    "Configuração do segredo JWT inválida.",
                    exception.getMessage()
            );

            assertNotNull(exception.getCause());
        }

        @Test
        @DisplayName("Deve rejeitar chave com tamanho insuficiente")
        void shouldRejectWeakSecret() {
            String weakSecret = Base64.getEncoder()
                    .encodeToString("chave-curta".getBytes());

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new JwtService(weakSecret)
            );

            assertEquals(
                    "Configuração do segredo JWT inválida.",
                    exception.getMessage()
            );

            assertNotNull(exception.getCause());
        }
    }

    private User createEntityUser() {
        return new User(
                "test.user",
                "test@test.com",
                "encoded-password",
                Role.EMPLOYEE
        );
    }
}