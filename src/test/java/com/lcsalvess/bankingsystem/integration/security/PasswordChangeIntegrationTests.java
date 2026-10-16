package com.lcsalvess.bankingsystem.integration.security;

import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo de troca de senha contra o PostgreSQL: login, troca,
 * revogação dos tokens anteriores e novo login.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeIntegrationTests {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String PASSWORD_URL = "/api/v1/auth/password";
    private static final String USERNAME = "test-user";
    private static final String CURRENT_PASSWORD = "current-password";
    private static final String NEW_PASSWORD = "brand-new-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User(
                USERNAME,
                "test-user@test.com",
                passwordEncoder.encode(CURRENT_PASSWORD),
                Role.EMPLOYEE
        ));
    }

    @Test
    @DisplayName("Deve trocar a senha e responder 204 sem corpo")
    void shouldChangePassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertEquals(1, user.getTokenVersion());
    }

    @Test
    @DisplayName("Deve aceitar a nova senha e rejeitar a antiga no login")
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
    @DisplayName("Deve invalidar o token usado na troca e qualquer token anterior")
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
    @DisplayName("Deve aceitar o token emitido depois da troca de senha")
    void shouldAcceptTokenIssuedAfterTheChange() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        String newToken = loginAndGetToken(NEW_PASSWORD);

        changePassword(newToken, NEW_PASSWORD, "another-password")
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deve rejeitar com 400 quando a senha atual está incorreta")
    void shouldRejectWhenCurrentPasswordIsIncorrect() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, "wrong-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INVALID_CURRENT_PASSWORD));

        // Nada mudou: o token e a senha antiga continuam válidos.
        login(CURRENT_PASSWORD).andExpect(status().isOk());
        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deve rejeitar com 400 quando a nova senha é igual à atual")
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
    @DisplayName("Deve rejeitar com 400 uma nova senha curta")
    void shouldRejectShortNewPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Deve rejeitar com 400 uma nova senha acima de 64 caracteres")
    void shouldRejectTooLongNewPassword() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        changePassword(token, CURRENT_PASSWORD, "a".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Deve rejeitar com 400 uma nova senha acima de 72 bytes")
    void shouldRejectNewPasswordAbove72Bytes() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        // 40 caracteres de 2 bytes = 80 bytes em UTF-8, dentro do limite de 64 caracteres.
        changePassword(token, CURRENT_PASSWORD, "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("Deve rejeitar com 400 campos ausentes")
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
    @DisplayName("Deve responder 401 sem token")
    void shouldRejectWithoutToken() throws Exception {
        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve responder 401 para usuário desativado")
    void shouldRejectDeactivatedUser() throws Exception {
        String token = loginAndGetToken(CURRENT_PASSWORD);

        User user = userRepository.findByUsernameIgnoreCase(USERNAME).orElseThrow();
        user.deactivate();
        userRepository.save(user);

        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized());
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
}
