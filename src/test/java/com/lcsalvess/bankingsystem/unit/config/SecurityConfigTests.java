package com.lcsalvess.bankingsystem.unit.config;

import com.lcsalvess.bankingsystem.config.JwtProperties;
import com.lcsalvess.bankingsystem.config.SecurityConfig;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a {@link SecurityConfig} com a cadeia de filtros real: {@code CorrelationIdFilter},
 * {@code JwtAuthenticationFilter}, {@link RestAuthenticationEntryPoint} e {@link JwtService}.
 * Somente o {@link CustomUserDetailsService} é substituído por mock (evita o banco de dados).
 * As propriedades {@code jwt.*} são vinculadas ao {@link JwtProperties} real.
 *
 * <p>Slices de controller não escaneiam {@code @Configuration}, por isso a
 * {@code SecurityConfig} é importada explicitamente. Os filtros ({@code @Component}) e o
 * {@code GlobalExceptionHandler} entram no slice por escaneamento, como em produção.
 *
 * <p>Não importa {@code WebMvcTestSecurityConfig}: aqui a segurança é real.
 *
 * <p>O slice não traz a auto-configuração do Spring Security, por isso o teste ativa
 * {@code @EnableWebSecurity} e monta o {@link MockMvc} só com o {@code springSecurityFilterChain}.
 * Assim a ordem dos filtros é a da {@link SecurityConfig}, e não a ordem de registro dos beans.
 */
@WebMvcTest(controllers = SecurityConfigTests.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        JwtService.class
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
    private static final String PROTECTED_URL = "/api/v1/probe";
    private static final String ADMIN_URL = "/api/v1/probe/admin";
    private static final String PUBLIC_URL = "/api/v1/auth/probe";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String UNAUTHORIZED_MESSAGE = "Não foi possível autenticar o usuário.";

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

        @Test
        @DisplayName("Deve permitir /api/v1/auth/** sem token e sem CSRF")
        void shouldAllowAuthRoutesWithoutTokenAndWithoutCsrf() throws Exception {
            mockMvc.perform(post(PUBLIC_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().string("public"));

            verifyNoInteractions(userDetailsService);
        }

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
            user.setActive(false);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Deve rejeitar token inválido mesmo em rota pública (comportamento atual do filtro)")
        void shouldRejectInvalidTokenEvenOnPublicRoute() throws Exception {
            // O JwtAuthenticationFilter roda em todas as rotas: um cliente que reenvia um
            // token expirado para /api/v1/auth/** recebe 401 em vez de poder autenticar.
            // Se o filtro passar a ignorar rotas públicas, inverta esta asserção.
            expectUnauthorized(mockMvc.perform(post(PUBLIC_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")));
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
        @DisplayName("Não deve exigir token CSRF em requisições POST autenticadas")
        void shouldNotRequireCsrfTokenForAuthenticatedPost() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(post(PROTECTED_URL)
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
    @DisplayName("Correlation ID na cadeia de filtros")
    class CorrelationIdTests {

        @Test
        @DisplayName("Deve gerar o header quando a requisição não o envia")
        void shouldGenerateCorrelationIdWhenMissing() throws Exception {
            MvcResult result = mockMvc.perform(post(PUBLIC_URL)).andReturn();

            String correlationId = result.getResponse().getHeader(CORRELATION_ID_HEADER);

            assertNotNull(correlationId);
            assertDoesNotThrow(() -> UUID.fromString(correlationId));
        }

        @Test
        @DisplayName("Deve repetir o header recebido quando for um UUID válido")
        void shouldEchoValidCorrelationId() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(post(PUBLIC_URL)
                    .header(CORRELATION_ID_HEADER, correlationId)).andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
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
            User user = createUserWithEncodedPassword(true);
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
                    .thenReturn(createUserWithEncodedPassword(true));

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
        @DisplayName("Deve rejeitar usuário inativo mesmo com a senha correta")
        void shouldRejectDisabledUserEvenWithCorrectPassword() {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(createUserWithEncodedPassword(false));

            assertThrows(DisabledException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, PASSWORD)));
        }
    }

    private void expectUnauthorized(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private static JwtService serviceOf(String secret, long expirationInSeconds, String issuer) {
        return new JwtService(new JwtProperties(secret, expirationInSeconds, issuer));
    }

    private static User createUser(Role role) {
        return new User(USERNAME, "usuario.teste@email.com", "encoded-password", role);
    }

    private User createUserWithEncodedPassword(boolean active) {
        User user = new User(
                USERNAME,
                "usuario.teste@email.com",
                passwordEncoder.encode(PASSWORD),
                Role.EMPLOYEE
        );
        user.setActive(active);
        return user;
    }

    /**
     * Endpoints mínimos para exercitar as regras da {@link SecurityConfig}.
     * É detectado por escaneamento porque está listado em {@code controllers} do {@code @WebMvcTest}.
     */
    @RestController
    public static class ProbeController {

        @PostMapping("/api/v1/auth/probe")
        public String publicRoute() {
            return "public";
        }

        @GetMapping("/api/v1/probe")
        public String whoAmI(Authentication authentication) {
            return authentication.getName();
        }

        @PostMapping("/api/v1/probe")
        public String create() {
            return "created";
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/v1/probe/admin")
        public String adminOnly() {
            return "admin";
        }
    }
}