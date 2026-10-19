package com.lcsalvess.bankingsystem.unit.config.security;

import com.lcsalvess.bankingsystem.config.time.ClockConfig;
import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.config.security.SecurityConfig;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.securityprobetest.SecurityProbeController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests {@link SecurityConfig} with the real security filter chain:
 * {@code CorrelationIdFilter}, {@code JwtAuthenticationFilter},
 * {@link RestAuthenticationEntryPoint} and {@link JwtService}.
 * Only {@link CustomUserDetailsService} is replaced with a mock to avoid
 * database access. The real {@link JwtProperties} configuration is used.
 *
 * <p>This test uses a restricted {@code @WebMvcTest} slice. The security
 * configuration and its required dependencies are imported explicitly.
 * MVC-supported filters and exception handlers are included in the slice
 * according to Spring Boot's test configuration.
 *
 * <p>{@code SecurityProbeController} is explicitly selected by
 * {@code @WebMvcTest(controllers = SecurityProbeController.class)}.
 * It resides outside the application's component-scan package so test-only
 * request mappings are not registered in the full application context.
 *
 * <p>{@code WebMvcTestSecurityConfig} is intentionally not imported because
 * this test exercises the real security configuration.
 *
 * <p>The test builds {@link MockMvc} with the actual
 * {@code springSecurityFilterChain} to preserve the configured filter order.
 */
@WebMvcTest(controllers = SecurityProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        ClockConfig.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtService.class,
        SecurityProbeController.class
})
@TestPropertySource(properties = {
        "jwt.secret=" + SecurityConfigTests.SECRET,
        "jwt.expiration=3600",
        "jwt.issuer=" + SecurityConfigTests.ISSUER
})
class SecurityConfigTests {

    // Base64 de "uma-chave-secreta-com-pelo-menos-32-bytes"
    static final String SECRET =
            "dW1hLWNoYXZlLXNlY3JldGEtY29tLXBlbG8tbWVub3MtMzItYnl0ZXM=";

    // Base64 de "outra-chave-secreta-completamente-diferente-64"
    private static final String OTHER_SECRET =
            "b3V0cmEtY2hhdmUtc2VjcmV0YS1jb21wbGV0YW1lbnRlLWRpZmVyZW50ZS02NA==";

    static final String ISSUER = "banking-system-test";

    private static final String USERNAME = "usuario.teste";
    private static final String PASSWORD = "Senha123";
    private static final String PROTECTED_URL = "/api/v1/transactions/probe";
    private static final String POST_PROTECTED_URL = "/api/v1/transactions/deposit";
    private static final String ADMIN_URL = "/api/v1/users/test-probe";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String AUTH_PROBE_URL = "/api/v1/auth/probe";
    private static final String PASSWORD_URL = "/api/v1/auth/password";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUpMockMvc() {
        Filter springSecurityFilterChain =
                context.getBean("springSecurityFilterChain", Filter.class);

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Nested
    @DisplayName("Rotas públicas")
    class PublicRoutesTests {
        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        })
        @DisplayName("Deve permitir a documentação OpenAPI sem token")
        void shouldAllowSwaggerRoutesWithoutToken(String url) throws Exception {
            MvcResult result = mockMvc.perform(get(url)).andReturn();

            int statusCode = result.getResponse().getStatus();
            assertNotEquals(401, statusCode, "A rota deveria ser pública");
            assertNotEquals(403, statusCode, "A rota deveria ser pública");

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve permitir POST /api/v1/auth/login sem token e sem CSRF")
        void shouldAllowLoginWithoutTokenAndWithoutCsrf() throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL))
                    .andReturn();

            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: {0}")
        @ValueSource(strings = {
                "Bearer token-invalido",
                "Bearer a.b.c",
                "Bearer ",
                "Bearer    "
        })
        @DisplayName("Deve permitir o login mesmo com um token inválido no header")
        void shouldAllowLoginWithInvalidToken(String authorization) throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andReturn();

            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve permitir o login com um token expirado no header")
        void shouldAllowLoginWithExpiredToken() throws Exception {
            String token = serviceOf(SECRET, -60, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn();

            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Não deve consultar o usuário no login mesmo com um token válido")
        void shouldNotLoadUserOnLoginWithValidToken() throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(createUser(Role.EMPLOYEE))
                            ))
                    .andReturn();

            assertNotEquals(401, result.getResponse().getStatus());
            assertNotEquals(403, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve proteger outros métodos HTTP no caminho do login")
        void shouldProtectOnlyPostOnLogin() throws Exception {
            expectUnauthorized(mockMvc.perform(get(LOGIN_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve exigir autenticação em outras rotas de auth")
        void shouldProtectOtherAuthRoutes() throws Exception {
            expectUnauthorized(mockMvc.perform(post(AUTH_PROBE_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve rejeitar token inválido em outras rotas de auth")
        void shouldRejectInvalidTokenOnOtherAuthRoutes() throws Exception {
            expectUnauthorized(mockMvc.perform(post(AUTH_PROBE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")));

            verifyNoInteractions(userDetailsService);
        }
    }

    @Nested
    @DisplayName("Rotas protegidas sem credenciais válidas")
    class ProtectedRoutesTests {

        @Test
        @DisplayName("Deve responder 401 em JSON quando não há token")
        void shouldReturn401WhenTokenIsMissing() throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {"/actuator/health", "/actuator/metrics", "/api/v1/qualquer-rota"})
        @DisplayName("Deve proteger qualquer rota não pública, inclusive /actuator/**")
        void shouldProtectEveryNonPublicRoute(String url) throws Exception {
            expectUnauthorized(mockMvc.perform(get(url)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve ignorar esquemas de autorização diferentes de Bearer")
        void shouldIgnoreNonBearerAuthorizationSchemes() throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXN1YXJpbzpzZW5oYQ==")));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: {0}")
        @ValueSource(strings = {"Bearer token-invalido", "Bearer a.b.c", "Bearer abc.def"})
        @DisplayName("Deve responder 401 em JSON para token malformado")
        void shouldReturn401ForMalformedToken(String authorization) throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorization)));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: \"{0}\"")
        @ValueSource(strings = {"Bearer ", "Bearer    "})
        @DisplayName("Deve responder 401 em JSON quando o Bearer vem sem token")
        void shouldReturn401WhenBearerHasNoToken(String authorization) throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorization)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve responder 401 para token expirado")
        void shouldReturn401ForExpiredToken() throws Exception {
            String token = serviceOf(SECRET, -60, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve responder 401 para token assinado com outra chave")
        void shouldReturn401ForTokenSignedWithAnotherKey() throws Exception {
            String token = serviceOf(OTHER_SECRET, 3600, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve responder 401 para token com issuer diferente")
        void shouldReturn401ForTokenWithDifferentIssuer() throws Exception {
            String token = serviceOf(SECRET, 3600, "outro-emissor")
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve responder 401 quando o usuário do token não existe mais")
        void shouldReturn401WhenTokenUserNoLongerExists() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new UsernameNotFoundException("Usuário ou senha inválidos."));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Deve responder 401 quando o usuário do token está inativo")
        void shouldReturn401WhenTokenUserIsDisabled() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            user.deactivate();
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }
    }

    @Nested
    @DisplayName("Rotas protegidas com token válido")
    class AuthenticatedRoutesTests {

        @Test
        @DisplayName("Deve autenticar o usuário do token e expô-lo à aplicação")
        void shouldAuthenticateTokenUser() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(USERNAME));

            verify(userDetailsService).loadUserByUsername(USERNAME);
            verifyNoMoreInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve negar uma rota de auth sem regra explícita, mesmo com token válido")
        void shouldDenyUnmatchedAuthRouteForAuthenticatedTokenUser() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(post(AUTH_PROBE_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Não deve exigir token CSRF em requisições POST autenticadas")
        void shouldNotRequireCsrfTokenForAuthenticatedPost() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(post(POST_PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("created"));
        }

        @Test
        @DisplayName("Deve ser stateless: sem sessão e sem cookie")
        void shouldBeStateless() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertNull(result.getRequest().getSession(false), "Nenhuma sessão deve ser criada");
            assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        }
    }

    @Nested
    @DisplayName("Falha de infraestrutura ao carregar o usuário do token")
    class InfrastructureFailureTests {

        @Test
        @DisplayName("Deve responder 503 em JSON, e não 401, quando o banco está indisponível")
        void shouldReturn503WhenDatabaseIsUnavailable() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("Sensitive connection detail"));

            expectServiceUnavailable(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Deve responder 503 em JSON quando não é possível abrir a transação")
        void shouldReturn503WhenTransactionCannotBeCreated() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new CannotCreateTransactionException("Sensitive transaction detail"));

            expectServiceUnavailable(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));
        }

        @Test
        @DisplayName("Não deve expor detalhes da falha na resposta")
        void shouldNotExposeFailureDetails() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("Sensitive connection detail"));

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE))))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("Sensitive connection detail")
                            )
                    ));
        }

        @Test
        @DisplayName("Deve incluir o correlation ID na resposta 503")
        void shouldIncludeCorrelationIdOnServiceUnavailableResponse() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("db down"));

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE))))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
        }
    }

    @Nested
    @DisplayName("Versão das credenciais do token")
    class TokenVersionTests {

        @Test
        @DisplayName("Deve responder 401 para token emitido antes da troca de senha")
        void shouldReturn401ForTokenIssuedBeforePasswordChange() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            String tokenBeforeChange = bearer(user);

            user.changePassword("novo-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, tokenBeforeChange)));
        }

        @Test
        @DisplayName("Deve aceitar token emitido depois da troca de senha")
        void shouldAcceptTokenIssuedAfterPasswordChange() throws Exception {
            User user = createUser(Role.EMPLOYEE);

            user.changePassword("novo-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(USERNAME));
        }

        @Test
        @DisplayName("Deve aceitar token sem o claim de versão enquanto o usuário está na versão 0")
        void shouldAcceptLegacyTokenWhileUserIsAtVersionZero() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Deve responder 401 para token sem o claim de versão depois da troca de senha")
        void shouldReturn401ForLegacyTokenAfterPasswordChange() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            user.changePassword("novo-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyToken())));
        }

        @Test
        @DisplayName("Deve responder 401 para token com claim tv inválido")
        void shouldReturn401ForTokenWithInvalidVersionClaim() throws Exception {
            String token = Jwts.builder()
                    .subject(USERNAME)
                    .issuer(ISSUER)
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("tv", "invalid")
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                    .compact();

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }
    }

    @Nested
    @DisplayName("Rota de troca de senha")
    class PasswordRouteTests {

        @Test
        @DisplayName("Deve responder 401 em JSON sem token")
        void shouldReturn401WithoutToken() throws Exception {
            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve responder 401 com token inválido")
        void shouldReturn401WithInvalidToken() throws Exception {
            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Deve permitir EMPLOYEE autenticado, sem exigir CSRF")
        void shouldAllowAuthenticatedEmployee() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            mockMvc.perform(patch(PASSWORD_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("password-probe"));
        }

        @Test
        @DisplayName("Deve permitir ADMIN autenticado")
        void shouldAllowAuthenticatedAdmin() throws Exception {
            User admin = createUser(Role.ADMIN);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(admin);

            mockMvc.perform(patch(PASSWORD_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Deve responder 401 para token anterior à última troca de senha")
        void shouldReturn401ForRevokedToken() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            String revokedToken = bearer(employee);

            employee.changePassword("novo-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)
                    .header(HttpHeaders.AUTHORIZATION, revokedToken)));
        }
    }

    @Nested
    @DisplayName("Autorização por perfil (@EnableMethodSecurity)")
    class MethodSecurityTests {

        @Test
        @DisplayName("Deve permitir ADMIN em rota restrita a administradores")
        void shouldAllowAdminOnAdminRoute() throws Exception {
            User admin = createUser(Role.ADMIN);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(admin);

            mockMvc.perform(get(ADMIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("admin"));
        }

        @Test
        @DisplayName("Deve responder 403 para EMPLOYEE em rota restrita a administradores")
        void shouldForbidEmployeeOnAdminRoute() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            mockMvc.perform(get(ADMIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Deve responder 401, e não 403, para anônimo em rota restrita a administradores")
        void shouldReturn401ForAnonymousOnAdminRoute() throws Exception {
            expectUnauthorized(mockMvc.perform(get(ADMIN_URL)));
        }
    }

    @Nested
    @DisplayName("Autorização do Actuator")
    class ActuatorAuthorizationTests {

        @Test
        @DisplayName("Deve responder 403 em JSON para EMPLOYEE nas métricas")
        void shouldReturn403JsonForEmployeeOnActuatorMetrics() throws Exception {
            User employee = createUser(Role.EMPLOYEE);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(employee);

            mockMvc.perform(get("/actuator/metrics")
                            .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(ApiErrorMessages.ACCESS_DENIED_RESOURCE))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }
    }

    @Nested
    @DisplayName("Correlation ID na cadeia de filtros")
    class CorrelationIdTests {
        @Test
        @DisplayName("Deve gerar o header quando a requisição não o envia")
        void shouldGenerateCorrelationIdWhenMissing() throws Exception {
            MvcResult result = mockMvc.perform(post(PROTECTED_URL))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            String correlationId =
                    result.getResponse().getHeader(CORRELATION_ID_HEADER);

            assertNotNull(correlationId);
            assertDoesNotThrow(() -> UUID.fromString(correlationId));
        }

        @Test
        @DisplayName("Deve repetir o header recebido quando for um UUID válido")
        void shouldEchoValidCorrelationId() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(post(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertEquals(
                    correlationId,
                    result.getResponse().getHeader(CORRELATION_ID_HEADER)
            );
        }

        @Test
        @DisplayName("Deve incluir o header também nas respostas 401")
        void shouldIncludeCorrelationIdOnUnauthorizedResponses() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("Deve incluir o header em respostas de token inválido rejeitado pelo filtro JWT")
        void shouldIncludeCorrelationIdWhenJwtFilterRejectsToken() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("Deve manter o registro automático do filtro desabilitado")
        void shouldKeepServletRegistrationDisabled() {
            assertFalse(correlationIdFilterRegistration.isEnabled());
            assertInstanceOf(CorrelationIdFilter.class, correlationIdFilterRegistration.getFilter());
        }
    }

    @Nested
    @DisplayName("Beans de autenticação")
    class AuthenticationBeansTests {

        @Test
        @DisplayName("Deve usar BCrypt como PasswordEncoder")
        void shouldUseBCryptPasswordEncoder() {
            assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);

            String hash = passwordEncoder.encode(PASSWORD);

            assertTrue(hash.startsWith("$2"), "O hash deve estar no formato BCrypt");
            assertNotEquals(PASSWORD, hash);
            assertTrue(passwordEncoder.matches(PASSWORD, hash));
            assertFalse(passwordEncoder.matches("SenhaErrada", hash));
            assertNotEquals(hash, passwordEncoder.encode(PASSWORD), "O salt deve variar a cada hash");
        }

        @Test
        @DisplayName("Deve autenticar com credenciais válidas")
        void shouldAuthenticateWithValidCredentials() {
            User user = createUserWithEncodedPassword();
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, PASSWORD));

            assertTrue(result.isAuthenticated());
            assertSame(user, result.getPrincipal());
            assertEquals(USERNAME, result.getName());
        }

        @Test
        @DisplayName("Deve rejeitar senha incorreta")
        void shouldRejectWrongPassword() {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(createUserWithEncodedPassword());

            assertThrows(BadCredentialsException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, "SenhaErrada")));
        }

        @Test
        @DisplayName("Deve tratar usuário inexistente como credenciais inválidas")
        void shouldTreatUnknownUserAsBadCredentials() {
            when(userDetailsService.loadUserByUsername(anyString()))
                    .thenThrow(new UsernameNotFoundException("Usuário ou senha inválidos."));

            assertThrows(BadCredentialsException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated("inexistente", PASSWORD)));
        }

        @Test
        @DisplayName("Deve sinalizar falha de banco como InternalAuthenticationServiceException, e não como credencial inválida")
        void shouldWrapDatabaseFailureAsInternalAuthenticationServiceException() {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("db down"));

            InternalAuthenticationServiceException exception = assertThrows(
                    InternalAuthenticationServiceException.class,
                    () -> authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, PASSWORD)
                    )
            );

            assertInstanceOf(DataAccessResourceFailureException.class, exception.getCause());
        }

        @Test
        @DisplayName("Deve rejeitar usuário inativo mesmo com a senha correta")
        void shouldRejectDisabledUserEvenWithCorrectPassword() {
            User user = createUserWithEncodedPassword();
            user.deactivate();

            assertFalse(user.isEnabled());

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(user);

            assertSame(user, userDetailsService.loadUserByUsername(USERNAME));

            assertThrows(DisabledException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    USERNAME, PASSWORD
                            )
                    ));
        }
    }

    private void expectUnauthorized(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.AUTHENTICATION_FAILED))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void expectServiceUnavailable(ResultActions actions) throws Exception {
        actions.andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /** Token como os emitidos antes da introdução do claim de versão (sem "tv"). */
    private static String legacyToken() {
        return Jwts.builder()
                .subject(USERNAME)
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private static JwtService serviceOf(String secret, long expirationInSeconds, String issuer) {
        return new JwtService(new JwtProperties(secret, expirationInSeconds, issuer), Clock.systemUTC());
    }

    private static User createUser(Role role) {
        return new User(USERNAME, "usuario.teste@email.com", "encoded-password", role);
    }

    private User createUserWithEncodedPassword() {
        return new User(
                USERNAME,
                "usuario.teste@email.com",
                passwordEncoder.encode(PASSWORD),
                Role.EMPLOYEE
        );
    }
}