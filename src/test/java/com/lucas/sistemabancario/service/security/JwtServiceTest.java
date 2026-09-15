package com.lucas.sistemabancario.service.security;

import com.lucas.sistemabancario.entity.User;
import com.lucas.sistemabancario.entity.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JwtServiceTest {
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        String secret = Base64.getEncoder().encodeToString("uma-chave-secreta-com-pelo-menos-32-bytes".getBytes());
        ReflectionTestUtils.setField(jwtService, "secret", secret);
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

    private User createEntityUser() {
        return new User(
                "test.user",
                "test@test.com",
                "encoded-password",
                Role.EMPLOYEE
        );
    }
}
