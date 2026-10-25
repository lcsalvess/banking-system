package com.lcsalvess.bankingsystem.unit.controller;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final AtomicInteger TEST_IP_COUNTER =
            new AtomicInteger(1);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RateLimitProtectionService rateLimitProtectionService;

    @Autowired
    private LoginRateLimitProperties rateLimitProperties;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();

        // Limpa as tentativas de login dos usuários utilizados nos testes.
        rateLimitProtectionService.clearFailures("test-user");
        rateLimitProtectionService.clearFailures("test-invalid-user");

        User user = new User(
                "test-user",
                "test-user@test.com",
                passwordEncoder.encode("test-password"),
                Role.EMPLOYEE
        );

        userRepository.save(user);
    }

    private String nextTestIp() {
        int lastOctet = TEST_IP_COUNTER.getAndUpdate(
                current -> current >= 254 ? 1 : current + 1
        );

        return "192.0.2." + lastOctet;
    }

    private RequestPostProcessor withNextTestIp() {
        return withIp(nextTestIp());
    }

    private RequestPostProcessor withIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions login(
            String username,
            String password,
            String ip
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(withIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "username": "%s",
                            "password": "%s"
                        }
                        """.formatted(username, password)));
    }

    @Nested
    @DisplayName("Ao tentar autenticar")
    class AuthTest {

        private static final String URL = "/api/v1/auth/login";

        @Test
        @DisplayName("Deve autenticar com credenciais válidas")
        void shouldAuthenticateWithValidCredentials() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
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
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
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
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
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
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "",
                                        "password": "test-invalid-password"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve rejeitar senha em branco")
        void shouldRejectBlankPassword() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "test-user",
                                        "password": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve rejeitar usuário inativo")
        void shouldRejectDisabledUser() throws Exception {
            User user = userRepository
                    .findByUsernameIgnoreCase("test-user")
                    .orElseThrow();

            user.deactivate();
            userRepository.save(user);

            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
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

    @Nested
    @DisplayName("Ao exceder o limite de tentativas de login")
    class RateLimitTests {

        private static final String USERNAME = "test-user";
        private static final String PASSWORD = "test-password";
        private static final String WRONG_PASSWORD = "test-wrong-password";

        @Test
        @DisplayName("Deve responder 429 com Retry-After após o limite de tentativas do usuário no mesmo IP")
        void shouldRespondTooManyRequestsAfterMaximumAttempts() throws Exception {
            String ip = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(USERNAME, WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(USERNAME, WRONG_PASSWORD, ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED));
        }

        @Test
        @DisplayName("Deve bloquear também a senha correta enquanto o usuário estiver bloqueado no IP")
        void shouldRejectCorrectPasswordWhileLocked() throws Exception {
            String ip = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(USERNAME, WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(USERNAME, PASSWORD, ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        }

        @Test
        @DisplayName("Não deve impedir o usuário legítimo de entrar a partir de outro IP")
        void shouldNotLockLegitimateUserFromAnotherIp() throws Exception {
            String attackerIp = nextTestIp();
            String userIp = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(USERNAME, WRONG_PASSWORD, attackerIp)
                        .andExpect(status().isUnauthorized());
            }

            login(USERNAME, WRONG_PASSWORD, attackerIp)
                    .andExpect(status().isTooManyRequests());

            login(USERNAME, PASSWORD, userIp)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @DisplayName("Deve zerar a contagem após um login bem-sucedido")
        void shouldResetCountAfterSuccessfulLogin() throws Exception {
            String ip = nextTestIp();
            int maxAttempts = rateLimitProperties.maxFailedAttempts();

            for (int i = 0; i < maxAttempts - 1; i++) {
                login(USERNAME, WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(USERNAME, PASSWORD, ip)
                    .andExpect(status().isOk());

            for (int i = 0; i < maxAttempts; i++) {
                login(USERNAME, WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(USERNAME, WRONG_PASSWORD, ip)
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Deve limitar as requisições de um mesmo IP, ainda que com usuários diferentes")
        void shouldLimitRequestsPerIp() throws Exception {
            String ip = nextTestIp();
            int capacity = rateLimitProperties.ipCapacity();

            int accepted = 0;
            boolean limited = false;

            // O bucket recebe tokens com o tempo, então a tentativa seguinte à
            // capacidade pode passar se o laço for lento. Por isso há uma folga.
            for (int i = 0; i < capacity + 10 && !limited; i++) {
                ResultActions result = login(
                        "test-iplimit-" + i,
                        WRONG_PASSWORD,
                        ip
                );

                int status = result.andReturn().getResponse().getStatus();

                if (status == 429) {
                    limited = true;

                    result.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
                } else {
                    assertEquals(401, status);
                    accepted++;
                }
            }

            assertTrue(limited, "O limite de IP deveria ter sido atingido.");
            assertTrue(
                    accepted >= capacity,
                    "As requisições dentro da capacidade deveriam ser aceitas."
            );
        }
    }
}
