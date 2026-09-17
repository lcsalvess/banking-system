package com.lucas.sistemabancario.controller.security;

import com.lucas.sistemabancario.entity.User;
import com.lucas.sistemabancario.entity.enums.Role;
import com.lucas.sistemabancario.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = new User(
                "usuario.teste",
                "usuario.teste@email.com",
                passwordEncoder.encode("Senha123"),
                Role.EMPLOYEE
        );

        userRepository.save(user);
    }

    @Nested
    @DisplayName("Ao tentar autenticar")
    class AuthTest {
        @Test
        @DisplayName("Deve autenticar com credenciais válidas")
        void shouldAuthenticateWithValidCredentials() throws Exception {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "usuario.teste",
                        "password": "Senha123"
                    }
                    """)).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.username").value("usuario.teste"));
        }

        @Test
        @DisplayName("Deve rejeitar senha inválida")
        void shouldRejectInvalidPassword() throws Exception {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "usuario.teste",
                        "password": "SenhaErrada123"
                    }
                    """)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Deve rejeitar usuário inexistente")
        void shouldRejectNonexistentUser() throws Exception {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "usuario.inexistente",
                        "password": "Senha123"
                    }
                    """)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Deve rejeitar username em branco")
        void shouldRejectBlankUsername() throws Exception {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "",
                        "password": "Senha123"
                    }
                    """)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve rejeitar senha em branco")
        void shouldRejectBlankPassword() throws Exception {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "usuario.teste",
                        "password": ""
                    }
                    """)).andExpect(status().isBadRequest());
        }
    }
}

