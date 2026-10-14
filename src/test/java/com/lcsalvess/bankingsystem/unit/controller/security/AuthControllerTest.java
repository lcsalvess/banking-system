package com.lcsalvess.bankingsystem.unit.controller.security;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
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

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User(
                "test-user",
                "test-user@test.com",
                passwordEncoder.encode("test-password"),
                Role.EMPLOYEE
        );

        userRepository.save(user);
    }

    @Nested
    @DisplayName("Ao tentar autenticar")
    class AuthTest {

        private static final String URL = "/api/v1/auth/login";

        @Test
        @DisplayName("Deve autenticar com credenciais válidas")
        void shouldAuthenticateWithValidCredentials() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "test-user",
                        "password": "test-password"
                    }
                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.username").value("test-user"));
        }

        @Test
        @DisplayName("Deve rejeitar senha inválida")
        void shouldRejectInvalidPassword() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "test-user",
                        "password": "test-invalid-password"
                    }
                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Deve rejeitar usuário inexistente")
        void shouldRejectNonexistentUser() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "test-invalid-user",
                        "password": "test-invalid-password"
                    }
                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Deve rejeitar username em branco")
        void shouldRejectBlankUsername() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "",
                        "password": "test-invalid-password"
                    }
                    """)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve rejeitar senha em branco")
        void shouldRejectBlankPassword() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                        "username": "test-user",
                        "password": ""
                    }
                    """)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve rejeitar usuário inativo")
        void shouldRejectDisabledUser() throws Exception {
            User user = userRepository.findByUsernameIgnoreCase("test-user")
                    .orElseThrow();

            user.deactivate();
            userRepository.save(user);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                    {
                        "username": "test-user",
                        "password": "test-password"
                    }
                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.INVALID_CREDENTIALS));
        }
    }
}

