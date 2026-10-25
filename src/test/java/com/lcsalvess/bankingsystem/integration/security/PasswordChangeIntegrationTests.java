package com.lcsalvess.bankingsystem.integration.security;

import com.lcsalvess.bankingsystem.config.security.LoginRateLimitProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.user.UserService;
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
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private static final String CURRENT_PASSWORD = "Currentpassword123!";
    private static final String NEW_PASSWORD = "BrandNewPassword456!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimitProperties rateLimitProperties;

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

        changePassword(newToken, NEW_PASSWORD, "Another-password123!")
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
    @DisplayName("Deve responder 429 após tentativas repetidas com a senha atual incorreta, sem bloquear o login")
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

        // Enquanto durar o bloqueio, nem a senha correta é avaliada.
        changePassword(token, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests());

        // O bloqueio da troca de senha não afeta o login.
        login(CURRENT_PASSWORD).andExpect(status().isOk());
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

    @Test
    @DisplayName(
            "Deve manter tokens antigos inválidos após desativar e reativar o usuário"
    )
    void shouldKeepOldTokenRevokedAfterReactivation() throws Exception {
        String oldToken = loginAndGetToken(CURRENT_PASSWORD);

        User user = userRepository
                .findByUsernameIgnoreCase(USERNAME)
                .orElseThrow();

        userService.deactivate(user.getId());
        userService.reactivate(user.getId());

        User reactivatedUser = userRepository
                .findByUsernameIgnoreCase(USERNAME)
                .orElseThrow();

        assertTrue(reactivatedUser.isActive());

        assertEquals(
                1,
                reactivatedUser.getTokenVersion(),
                "A desativação deve incrementar a versão de segurança uma vez."
        );

        // O token emitido antes da desativação continua inválido.
        changePassword(oldToken, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized());

        // Um novo login deve emitir um token válido.
        login(CURRENT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName(
            "Deve permitir apenas uma troca de senha quando duas requisições ocorrem simultaneamente"
    )
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

            // Garante que as duas tarefas estejam prontas antes da largada.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "As duas requisições deveriam estar prontas para iniciar."
            );

            // Libera as duas requisições praticamente ao mesmo tempo.
            start.countDown();

            int firstStatus = firstResult.get(20, TimeUnit.SECONDS);
            int secondStatus = secondResult.get(20, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(firstStatus, secondStatus);

            // Exatamente uma troca deve ser concluída.
            assertEquals(
                    1L,
                    statuses.stream()
                            .filter(status -> status == HttpStatus.NO_CONTENT.value())
                            .count(),
                    "Exatamente uma troca de senha deve ser bem-sucedida."
            );

            // A segunda solicitação pode receber 400 se chegar ao serviço
            // e a senha antiga já não corresponder, ou 401 se o filtro JWT
            // detectar que o token foi invalidado.
            assertTrue(
                    statuses.stream().allMatch(status ->
                            status == HttpStatus.NO_CONTENT.value()
                                    || status == HttpStatus.BAD_REQUEST.value()
                                    || status == HttpStatus.UNAUTHORIZED.value()
                    ),
                    "As respostas devem ser 204, 400 ou 401. Recebidas: " + statuses
            );

            User savedUser = userRepository
                    .findByUsernameIgnoreCase(USERNAME)
                    .orElseThrow();

            // A versão não pode sofrer atualização perdida.
            assertEquals(
                    1,
                    savedUser.getTokenVersion(),
                    "A versão das credenciais deve ser incrementada exatamente uma vez."
            );

            boolean firstPasswordWon = passwordEncoder.matches(
                    firstNewPassword,
                    savedUser.getPassword()
            );

            boolean secondPasswordWon = passwordEncoder.matches(
                    secondNewPassword,
                    savedUser.getPassword()
            );

            // Exatamente uma das novas senhas deve corresponder ao hash persistido.
            assertTrue(
                    firstPasswordWon ^ secondPasswordWon,
                    "Apenas uma das novas senhas deve estar armazenada."
            );

            String winningPassword = firstPasswordWon
                    ? firstNewPassword
                    : secondNewPassword;

            // A senha antiga não deve mais autenticar.
            login(CURRENT_PASSWORD)
                    .andExpect(status().isUnauthorized());

            // A senha vencedora deve autenticar normalmente.
            login(winningPassword)
                    .andExpect(status().isOk());
        } finally {
            // Evita deixar tarefas aguardando caso alguma asserção falhe.
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
                    "Tempo excedido aguardando o início do teste concorrente."
            );
        }

        return changePassword(token, currentPassword, newPassword)
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
