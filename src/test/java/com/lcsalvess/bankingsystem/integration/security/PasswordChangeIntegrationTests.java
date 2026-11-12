package com.lcsalvess.bankingsystem.integration.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.UserService;
import com.lcsalvess.bankingsystem.testsupport.LedgerTestCleaner;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end password change flow against PostgreSQL: login, password change,
 * revocation of previous tokens, and a new login.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTests {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String PASSWORD_URL = "/api/v1/auth/password";
    private static final String USERNAME = "test-user";
    private static final String CURRENT_PASSWORD = "Currentpassword123!";
    private static final String NEW_PASSWORD = "BrandNewPassword456!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private LedgerTestCleaner ledgerTestCleaner;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimitProperties rateLimitProperties;

    @BeforeEach
    void setUp() {
        ledgerTestCleaner.deleteAllTransactions();
        userRepository.deleteAll();

        userRepository.save(
                UserTestFactory.aUser()
                        .withEmployeeId("EMP-TEST-001")
                        .withFullName("Test User")
                        .withUsername(USERNAME)
                        .withEmail("test-user@test.com")
                        .withEncodedPassword(passwordEncoder.encode(CURRENT_PASSWORD))
                        .build()
        );
    }

    @Test
    @DisplayName("Should change the password and return 204 without a response body")
    void shouldChangePassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    @DisplayName("Should accept the new password and reject the old password during login")
    void shouldLoginOnlyWithNewPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        login(CURRENT_PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Should invalidate the token used for the password change and all previously issued tokens")
    void shouldRevokeTokensIssuedBeforeTheChange() throws Exception {
        String tokenUsedOnChange = loginAndGetToken(CURRENT_PASSWORD);
        String otherSessionToken = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(tokenUsedOnChange, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        changePassword(tokenUsedOnChange, NEW_PASSWORD, "another-password")
                .andExpect(status().isUnauthorized());
        changePassword(otherSessionToken, NEW_PASSWORD, "another-password")
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should accept a token issued after the password change")
    void shouldAcceptTokenIssuedAfterTheChange() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        String newToken = loginAndGetToken(NEW_PASSWORD);

        changePassword(newToken, NEW_PASSWORD, "Another-password123!")
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return 400 when the current password is incorrect")
    void shouldRejectWhenCurrentPasswordIsIncorrect() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, "wrong-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INVALID_CURRENT_PASSWORD));

        // Nothing should change: the token and the old password must remain valid.
        login(CURRENT_PASSWORD).andExpect(status().isOk());
        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return 429 after repeated incorrect current password attempts without blocking login")
    void shouldRateLimitRepeatedWrongCurrentPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        for (int i = 0; i < rateLimitProperties.maxFailedAttempts(); i++) {
            changePassword(token, "wrong-password", NEW_PASSWORD)
                    .andExpect(status().isBadRequest());
        }

        changePassword(token, "wrong-password", NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED));

        // While the account is rate-limited, even the correct password must not be evaluated.
        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests());

        // The password change rate limit must not affect login.
        login(CURRENT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 400 when the new password matches the current password")
    void shouldRejectWhenNewPasswordEqualsCurrent() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, CURRENT_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.PASSWORD_UNCHANGED));

        assertEquals(
                0,
                userRepository.findByUsernameIgnoreCase(USERNAME)
                        .orElseThrow()
                        .getTokenVersion()
        );
    }

    @Test
    @DisplayName("Should return 400 when the new password is too short")
    void shouldRejectShortNewPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Should return 400 when the new password exceeds 64 characters")
    void shouldRejectTooLongNewPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, "a".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Should return 400 when the new password exceeds 72 bytes")
    void shouldRejectNewPasswordAbove72Bytes() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        // 40 two-byte characters = 80 bytes in UTF-8, within the 64-character limit.
        changePassword(token, CURRENT_PASSWORD, "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Should return 400 when required fields are missing")
    void shouldRejectMissingFields() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        mockMvc.perform(patch(PASSWORD_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Should return 401 when no token is provided")
    void shouldRejectWithoutToken() throws Exception {
        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 for a deactivated user")
    void shouldRejectDeactivatedUser() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        user.deactivate();
        userRepository.save(user);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should keep old tokens invalid after deactivating and reactivating the user")
    void shouldKeepOldTokenRevokedAfterReactivation() throws Exception {
        String oldToken = loginAndGetToken(CURRENT_PASSWORD);

        User user = userRepository
                .findByUsernameIgnoreCase(USERNAME)
                .orElseThrow();

        userService.deactivate(user.getEmployeeId());
        userService.reactivate(user.getEmployeeId());

        User reactivatedUser = userRepository
                .findByUsernameIgnoreCase(USERNAME)
                .orElseThrow();

        assertTrue(reactivatedUser.isActive());

        assertEquals(
                1,
                reactivatedUser.getTokenVersion(),
                "Deactivation should increment the security version exactly once."
        );

        // The token issued before deactivation must remain invalid.
        changePassword(oldToken, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized());

        // A new login should issue a valid token.
        login(CURRENT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Should allow only one password change when two requests are submitted concurrently")
    void shouldSerializeConcurrentPasswordChanges() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        String firstNewPassword = "First-New-Password123!";
        String secondNewPassword = "Second-New-Password456!";

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> firstResult = executor.submit(
                    () -> changePasswordAfterStart(
                            token,
                            CURRENT_PASSWORD,
                            firstNewPassword,
                            ready,
                            start
                    )
            );

            Future<Integer> secondResult = executor.submit(
                    () -> changePasswordAfterStart(
                            token,
                            CURRENT_PASSWORD,
                            secondNewPassword,
                            ready,
                            start
                    )
            );

            // Ensure both tasks are ready before releasing them.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests should be ready to start."
            );

            // Release both requests at nearly the same time.
            start.countDown();

            int firstStatus = firstResult.get(20, TimeUnit.SECONDS);
            int secondStatus = secondResult.get(20, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(firstStatus, secondStatus);

            // Exactly one password change should succeed.
            assertEquals(
                    1L,
                    statuses.stream()
                            .filter(status -> status == HttpStatus.NO_CONTENT.value())
                            .count(),
                    "Exactly one password change should succeed."
            );

            // The second request may receive 400 if it reaches the service
            // after the old password has already changed, or 401 if the JWT filter
            // detects that the token has been invalidated.
            assertTrue(
                    statuses.stream().allMatch(status ->
                            status == HttpStatus.NO_CONTENT.value()
                                    || status == HttpStatus.BAD_REQUEST.value()
                                    || status == HttpStatus.UNAUTHORIZED.value()
                    ),
                    "Responses must be 204, 400, or 401. Received: " + statuses
            );

            User savedUser = userRepository
                    .findByUsernameIgnoreCase(USERNAME)
                    .orElseThrow();

            // The security version must not suffer a lost update.
            assertEquals(
                    1,
                    savedUser.getTokenVersion(),
                    "The security version must be incremented exactly once."
            );

            boolean firstPasswordWon = passwordEncoder.matches(
                    firstNewPassword,
                    savedUser.getPassword()
            );

            boolean secondPasswordWon = passwordEncoder.matches(
                    secondNewPassword,
                    savedUser.getPassword()
            );

            // Exactly one of the new passwords must match the persisted hash.
            assertTrue(
                    firstPasswordWon ^ secondPasswordWon,
                    "Only one of the new passwords should be stored."
            );

            String winningPassword = firstPasswordWon
                    ? firstNewPassword
                    : secondNewPassword;

            // The old password must no longer authenticate.
            login(CURRENT_PASSWORD)
                    .andExpect(status().isUnauthorized());

            // The winning password must authenticate successfully.
            login(winningPassword)
                    .andExpect(status().isOk());
        } finally {
            // Prevent tasks from remaining blocked if an assertion fails.
            start.countDown();
            executor.shutdownNow();
        }
    }

    private ResultActions changePassword(
            String token,
            String currentPassword,
            String newPassword
    ) throws Exception {
        return mockMvc.perform(patch(PASSWORD_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(currentPassword, newPassword)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "username": "%s",
                            "password": "%s"
                        }
                        """.formatted(USERNAME, password)));
    }

    private String loginAndGetToken(String password) throws Exception {
        String json = login(password)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(json, "$.token");
    }

    private String body(String currentPassword, String newPassword) {
        return """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(currentPassword, newPassword);
    }

    private int changePasswordAfterStart(
            String token,
            String currentPassword,
            String newPassword,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting for the concurrent test to start."
            );
        }

        return changePassword(token, currentPassword, newPassword)
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
