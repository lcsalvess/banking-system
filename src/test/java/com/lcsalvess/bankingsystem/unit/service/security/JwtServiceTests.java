package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTests {

    private static final String SECRET =
            Base64.getEncoder()
                    .encodeToString("uma-chave-secreta-com-pelo-menos-32-bytes".getBytes());

    private static final long EXPIRATION_IN_SECONDS = 3600;

    private JwtService jwtService;
    private User user;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_IN_SECONDS);

        signingKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(SECRET)
        );

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

        @Test
        @DisplayName("Deve definir a expiração conforme configurado")
        void shouldSetExpirationAccordingToConfiguration() {
            String token = jwtService.generateToken(user);

            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();

            long expirationInSeconds =
                    (expiration.getTime() - issuedAt.getTime()) / 1000;

            assertEquals(EXPIRATION_IN_SECONDS, expirationInSeconds);
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
                    () -> new JwtService(null, EXPIRATION_IN_SECONDS)
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
                    () -> new JwtService(" ", EXPIRATION_IN_SECONDS)
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
                    () -> new JwtService("!!!", EXPIRATION_IN_SECONDS)
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
                    () -> new JwtService(weakSecret, EXPIRATION_IN_SECONDS)
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