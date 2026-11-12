package com.lcsalvess.bankingsystem.unit.controller.auth;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.security.ratelimit.RateLimitProtectionService;
import com.lcsalvess.bankingsystem.testsupport.LedgerTestCleaner;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
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
class AuthenticationControllerTests {

    private static final AtomicInteger TEST_IP_COUNTER =
            new AtomicInteger(1);

    private static final String TEST_PASSWORD = "test-password";
    private static final String NONEXISTENT_USERNAME = "test-invalid-user";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerTestCleaner ledgerTestCleaner;

    @Autowired
    private RateLimitProtectionService rateLimitProtectionService;

    @Autowired
    private LoginRateLimitProperties rateLimitProperties;

    private User testUser;

    @BeforeEach
    void setUp() {
        ledgerTestCleaner.deleteAllTransactions();
        userRepository.deleteAll();

        testUser = UserTestFactory.aUser()
                .withEncodedPassword(passwordEncoder.encode(TEST_PASSWORD))
                .build();

        // Clear login attempts for the users used in the tests.
        rateLimitProtectionService.clearFailures(testUser.getUsername());
        rateLimitProtectionService.clearFailures(NONEXISTENT_USERNAME);

        testUser = userRepository.save(testUser);
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
    @DisplayName("When authenticating")
    class AuthTest {

        private static final String URL = "/api/v1/auth/login";

        @Test
        @DisplayName("Should authenticate with valid credentials")
        void shouldAuthenticateWithValidCredentials() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "%s",
                                        "password": "%s"
                                    }
                                    """.formatted(testUser.getUsername(), TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.username").value(testUser.getUsername()));
        }

        @Test
        @DisplayName("Should reject an invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "%s",
                                        "password": "test-invalid-password"
                                    }
                                    """.formatted(testUser.getUsername())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject a nonexistent user")
        void shouldRejectNonexistentUser() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "%s",
                                        "password": "test-invalid-password"
                                    }
                                    """.formatted(NONEXISTENT_USERNAME)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject a blank username")
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
        @DisplayName("Should reject a blank password")
        void shouldRejectBlankPassword() throws Exception {
            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "%s",
                                        "password": ""
                                    }
                                    """.formatted(testUser.getUsername())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject an inactive user")
        void shouldRejectDisabledUser() throws Exception {
            User user = userRepository
                    .findByUsernameIgnoreCase(testUser.getUsername())
                    .orElseThrow();

            user.deactivate();
            userRepository.save(user);

            mockMvc.perform(post(URL)
                            .with(withNextTestIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "username": "%s",
                                        "password": "%s"
                                    }
                                    """.formatted(testUser.getUsername(), TEST_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.INVALID_CREDENTIALS));
        }
    }

    @Nested
    @DisplayName("When exceeding the login attempt limit")
    class RateLimitTests {

        private static final String WRONG_PASSWORD = "test-wrong-password";

        @Test
        @DisplayName("Should return 429 with Retry-After after exceeding the maximum login attempts from the same IP")
        void shouldRespondTooManyRequestsAfterMaximumAttempts() throws Exception {
            String ip = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(testUser.getUsername(), WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(testUser.getUsername(), WRONG_PASSWORD, ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message")
                            .value(ApiErrorMessages.LOGIN_RATE_LIMIT_EXCEEDED));
        }

        @Test
        @DisplayName("Should reject the correct password while the user is locked out from the IP")
        void shouldRejectCorrectPasswordWhileLocked() throws Exception {
            String ip = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(testUser.getUsername(), WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(testUser.getUsername(), TEST_PASSWORD, ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        }

        @Test
        @DisplayName("Should not prevent a legitimate user from logging in from another IP")
        void shouldNotLockLegitimateUserFromAnotherIp() throws Exception {
            String attackerIp = nextTestIp();
            String userIp = nextTestIp();

            for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
                login(testUser.getUsername(), WRONG_PASSWORD, attackerIp)
                        .andExpect(status().isUnauthorized());
            }

            login(testUser.getUsername(), WRONG_PASSWORD, attackerIp)
                    .andExpect(status().isTooManyRequests());

            login(testUser.getUsername(), TEST_PASSWORD, userIp)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @DisplayName("Should reset the failed login attempt count after a successful login")
        void shouldResetCountAfterSuccessfulLogin() throws Exception {
            String ip = nextTestIp();
            int maxAttempts = rateLimitProperties.maxFailedAttempts();

            for (int i = 0; i < maxAttempts - 1; i++) {
                login(testUser.getUsername(), WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(testUser.getUsername(), TEST_PASSWORD, ip)
                    .andExpect(status().isOk());

            for (int i = 0; i < maxAttempts; i++) {
                login(testUser.getUsername(), WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized());
            }

            login(testUser.getUsername(), WRONG_PASSWORD, ip)
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Should limit requests from the same IP even when different usernames are used")
        void shouldLimitRequestsPerIp() throws Exception {
            String ip = nextTestIp();
            int capacity = rateLimitProperties.ipCapacity();

            int accepted = 0;
            boolean limited = false;

            // The bucket replenishes tokens over time, so the request immediately
            // after reaching capacity may succeed if the loop takes too long.
            // A small margin is therefore included.
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

            assertTrue(limited, "The IP rate limit should have been reached.");
            assertTrue(
                    accepted >= capacity,
                    "Requests within the bucket capacity should be accepted."
            );
        }
    }
}
