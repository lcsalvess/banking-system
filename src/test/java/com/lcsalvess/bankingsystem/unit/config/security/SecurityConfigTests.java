package com.lcsalvess.bankingsystem.unit.config.security;

import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.config.security.JwtServiceConfiguration;
import com.lcsalvess.bankingsystem.config.security.SecurityConfig;
import com.lcsalvess.bankingsystem.config.time.ClockConfig;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
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
import org.springframework.test.context.ActiveProfiles;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests {@link SecurityConfig} with the real security filter chain:
 * {@code CorrelationIdFilter}, {@code JwtAuthenticationFilter},
 * {@link RestAuthenticationEntryPoint} and {@link JwtService}.
 * Only {@link CustomUserDetailsService} is replaced with a mock to avoid
 * database access. The real {@link JwtProperties} configuration is used.
 *
 * <p>This test uses the {@code test} profile to verify that OpenAPI routes
 * are not public outside the explicitly permitted local profile.
 *
 * <p>This test uses a restricted {@code @WebMvcTest} slice. The security
 * configuration and its required dependencies are imported explicitly.
 *
 * <p>{@code SecurityProbeController} is explicitly selected by
 * {@code @WebMvcTest(controllers = SecurityProbeController.class)}.
 * It resides outside the application's component-scan package so test-only
 * request mappings are not registered in the full application context.
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
        JwtServiceConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityProbeController.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/banking_system_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=false",

        // Enable Springdoc deliberately: security must still deny access
        // when the documentation endpoints are enabled outside local.
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",

        "jwt.secret=" + SecurityConfigTests.SECRET,
        "jwt.expiration=3600",
        "jwt.issuer=" + SecurityConfigTests.ISSUER
})
class SecurityConfigTests {

    // Base64 of "uma-chave-secreta-com-pelo-menos-32-bytes"
    static final String SECRET =
            "dW1hLWNoYXZlLXNlY3JldGEtY29tLXBlbG8tbWVub3MtMzItYnl0ZXM=";

    // Base64 of "outra-chave-secreta-completamente-diferente-64"
    private static final String OTHER_SECRET =
            "b3V0cmEtY2hhdmUtc2VjcmV0YS1jb21wbGV0YW1lbnRlLWRpZmVyZW50ZS02NA==";

    static final String ISSUER = "banking-system-test";

    private static final String USERNAME = "security.user.test";
    private static final String PASSWORD = "SecurityPassword123!";

    private static final String PROTECTED_URL = "/api/v1/transactions/probe";
    private static final String POST_PROTECTED_URL = "/api/v1/transactions/deposits";
    private static final String ADMIN_URL = "/api/v1/users/test-probe";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String AUTH_PROBE_URL = "/api/v1/auth/probe";
    private static final String PASSWORD_URL = "/api/v1/auth/password";
    private static final String AUDIT_EVENTS_URL = "/api/v1/audit-events/probe";
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
    @DisplayName("OpenAPI documentation protection")
    class OpenApiAuthorizationTests {

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        })
        @DisplayName("Should deny unauthenticated access to the documentation outside the local profile")
        void shouldDenyOpenApiRoutesWithoutToken(String url) throws Exception {
            expectUnauthorized(mockMvc.perform(get(url)));

            verifyNoInteractions(userDetailsService);
        }
    }

    @Nested
    @DisplayName("Public routes")
    class PublicRoutesTests {

        @Test
        @DisplayName("Should allow POST /api/v1/auth/login without a token and without CSRF")
        void shouldAllowLoginWithoutTokenAndWithoutCsrf() throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL))
                    .andReturn();

            assertEquals(404, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: {0}")
        @ValueSource(strings = {
                "Bearer invalid-token",
                "Bearer a.b.c",
                "Bearer ",
                "Bearer    "
        })
        @DisplayName("Should allow login even with an invalid token in the header")
        void shouldAllowLoginWithInvalidToken(String authorization) throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andReturn();

            assertEquals(404, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should allow login with an expired token in the header")
        void shouldAllowLoginWithExpiredToken() throws Exception {
            String token = serviceOf(SECRET, -60, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn();

            assertEquals(404, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should not load the user on login even with a valid token")
        void shouldNotLoadUserOnLoginWithValidToken() throws Exception {
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(createUser(Role.EMPLOYEE))
                            ))
                    .andReturn();

            assertEquals(404, result.getResponse().getStatus());

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should protect other HTTP methods on the login path")
        void shouldProtectOnlyPostOnLogin() throws Exception {
            expectUnauthorized(mockMvc.perform(get(LOGIN_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should require authentication on other auth routes")
        void shouldProtectOtherAuthRoutes() throws Exception {
            expectUnauthorized(mockMvc.perform(post(AUTH_PROBE_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should reject an invalid token on other auth routes")
        void shouldRejectInvalidTokenOnOtherAuthRoutes() throws Exception {
            expectUnauthorized(mockMvc.perform(post(AUTH_PROBE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")));

            verifyNoInteractions(userDetailsService);
        }
    }

    @Nested
    @DisplayName("Protected routes without valid credentials")
    class ProtectedRoutesTests {

        @Test
        @DisplayName("Should return 401 as JSON when there is no token")
        void shouldReturn401WhenTokenIsMissing() throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {
                "/actuator/health",
                "/actuator/info",
                "/actuator/metrics",
                "/api/v1/any-route"
        })
        @DisplayName("Should require authentication on protected routes")
        void shouldRequireAuthenticationOnProtectedRoutes(String url) throws Exception {
            expectUnauthorized(mockMvc.perform(get(url)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should ignore authorization schemes other than Bearer")
        void shouldIgnoreNonBearerAuthorizationSchemes() throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic dXN1YXJpbzpzZW5oYQ==")));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: {0}")
        @ValueSource(strings = {
                "Bearer invalid-token",
                "Bearer a.b.c",
                "Bearer abc.def"
        })
        @DisplayName("Should return 401 as JSON for a malformed token")
        void shouldReturn401ForMalformedToken(String authorization) throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorization)));

            verifyNoInteractions(userDetailsService);
        }

        @ParameterizedTest(name = "Authorization: \"{0}\"")
        @ValueSource(strings = {
                "Bearer ",
                "Bearer    "
        })
        @DisplayName("Should return 401 as JSON when the Bearer scheme has no token")
        void shouldReturn401WhenBearerHasNoToken(String authorization) throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, authorization)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 for an expired token")
        void shouldReturn401ForExpiredToken() throws Exception {
            String token = serviceOf(SECRET, -60, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 for a token signed with another key")
        void shouldReturn401ForTokenSignedWithAnotherKey() throws Exception {
            String token = serviceOf(OTHER_SECRET, 3600, ISSUER)
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 for a token with a different issuer")
        void shouldReturn401ForTokenWithDifferentIssuer() throws Exception {
            String token = serviceOf(SECRET, 3600, "another-issuer")
                    .generateToken(createUser(Role.EMPLOYEE));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 when the token user no longer exists")
        void shouldReturn401WhenTokenUserNoLongerExists() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new UsernameNotFoundException("Usuário ou senha inválidos."));

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should return 401 when the token user is inactive")
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
    @DisplayName("Protected routes with a valid token")
    class AuthenticatedRoutesTests {

        @Test
        @DisplayName("Should authenticate the token user and expose it to the application")
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
        @DisplayName("Should deny an auth route without an explicit rule, even with a valid token")
        void shouldDenyUnmatchedAuthRouteForAuthenticatedTokenUser() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(post(AUTH_PROBE_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should not require a CSRF token on authenticated POST requests")
        void shouldNotRequireCsrfTokenForAuthenticatedPost() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(post(POST_PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("created"));
        }

        @Test
        @DisplayName("Should be stateless: no session and no cookie")
        void shouldBeStateless() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertNull(result.getRequest().getSession(false), "No session should be created");
            assertNull(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        }

        @Test
        @DisplayName("Should deny transaction history access to ADMIN")
        void shouldForbidAdminFromTransactionHistory() throws Exception {
            User admin = createUser(Role.ADMIN);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(admin);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(admin)
                            ))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should deny transaction history access to AUDITOR")
        void shouldForbidAuditorFromTransactionHistory() throws Exception {
            User auditor = createUser(Role.AUDITOR);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(auditor);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(auditor)
                            ))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should allow AUDITOR on the audit events route")
        void shouldAllowAuditorOnAuditEventsRoute() throws Exception {
            User auditor = createUser(Role.AUDITOR);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(auditor);

            mockMvc.perform(get(AUDIT_EVENTS_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(auditor)
                            ))
                    .andExpect(status().isOk())
                    .andExpect(content().string("audit-events"));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should deny EMPLOYEE on the audit events route")
        void shouldForbidEmployeeOnAuditEventsRoute() throws Exception {
            User employee = createUser(Role.EMPLOYEE);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(employee);

            mockMvc.perform(get(AUDIT_EVENTS_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(employee)
                            ))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should deny ADMIN on the audit events route")
        void shouldForbidAdminOnAuditEventsRoute() throws Exception {
            User admin = createUser(Role.ADMIN);

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(admin);

            mockMvc.perform(get(AUDIT_EVENTS_URL)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    bearer(admin)
                            ))
                    .andExpect(status().isForbidden());

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }
    }

    @Nested
    @DisplayName("Infrastructure failure while loading the token user")
    class InfrastructureFailureTests {

        @Test
        @DisplayName("Should return 503 as JSON, not 401, when the database is unavailable")
        void shouldReturn503WhenDatabaseIsUnavailable() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("Sensitive connection detail"));

            expectServiceUnavailable(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));

            verify(userDetailsService).loadUserByUsername(USERNAME);
        }

        @Test
        @DisplayName("Should return 503 as JSON when a transaction cannot be opened")
        void shouldReturn503WhenTransactionCannotBeCreated() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new CannotCreateTransactionException("Sensitive transaction detail"));

            expectServiceUnavailable(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE)))));
        }

        @Test
        @DisplayName("Should not expose failure details in the response")
        void shouldNotExposeFailureDetails() throws Exception {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenThrow(new DataAccessResourceFailureException("Sensitive connection detail"));

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(createUser(Role.EMPLOYEE))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.not(
                                    org.hamcrest.Matchers.containsString("Sensitive connection detail")
                            )
                    ));
        }

        @Test
        @DisplayName("Should include the correlation ID in the 503 response")
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
    @DisplayName("Token credentials version")
    class TokenVersionTests {

        @Test
        @DisplayName("Should return 401 for a token issued before the password change")
        void shouldReturn401ForTokenIssuedBeforePasswordChange() throws Exception {
            User user = createUser(Role.EMPLOYEE);
            String tokenBeforeChange = bearer(user);

            user.changePassword("new-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(HttpHeaders.AUTHORIZATION, tokenBeforeChange)));
        }

        @Test
        @DisplayName("Should accept a token issued after the password change")
        void shouldAcceptTokenIssuedAfterPasswordChange() throws Exception {
            User user = createUser(Role.EMPLOYEE);

            user.changePassword("new-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);

            mockMvc.perform(get(PROTECTED_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(USERNAME));
        }

        @Test
        @DisplayName("Should return 401 for a token without the version claim")
        void shouldReturn401ForTokenWithoutVersionClaim() throws Exception {
            expectUnauthorized(mockMvc.perform(get(PROTECTED_URL)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + tokenWithoutVersion()
                    )));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 for a token with an invalid tv claim")
        void shouldReturn401ForTokenWithInvalidVersionClaim() throws Exception {
            String token = Jwts.builder()
                    .subject(USERNAME)
                    .issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now()))
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
    @DisplayName("Password change route")
    class PasswordRouteTests {

        @Test
        @DisplayName("Should return 401 as JSON without a token")
        void shouldReturn401WithoutToken() throws Exception {
            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should return 401 with an invalid token")
        void shouldReturn401WithInvalidToken() throws Exception {
            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Should allow an authenticated EMPLOYEE, without requiring CSRF")
        void shouldAllowAuthenticatedEmployee() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            mockMvc.perform(patch(PASSWORD_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("password-probe"));
        }

        @Test
        @DisplayName("Should allow an authenticated ADMIN")
        void shouldAllowAuthenticatedAdmin() throws Exception {
            User admin = createUser(Role.ADMIN);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(admin);

            mockMvc.perform(patch(PASSWORD_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 401 for a token issued before the last password change")
        void shouldReturn401ForRevokedToken() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            String revokedToken = bearer(employee);

            employee.changePassword("new-hash");
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            expectUnauthorized(mockMvc.perform(patch(PASSWORD_URL)
                    .header(HttpHeaders.AUTHORIZATION, revokedToken)));
        }
    }

    @Nested
    @DisplayName("Role-based authorization (@EnableMethodSecurity)")
    class MethodSecurityTests {

        @Test
        @DisplayName("Should allow ADMIN on an admin-only route")
        void shouldAllowAdminOnAdminRoute() throws Exception {
            User admin = createUser(Role.ADMIN);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(admin);

            mockMvc.perform(get(ADMIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("admin"));
        }

        @Test
        @DisplayName("Should return 403 for EMPLOYEE on an admin-only route")
        void shouldForbidEmployeeOnAdminRoute() throws Exception {
            User employee = createUser(Role.EMPLOYEE);
            when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(employee);

            mockMvc.perform(get(ADMIN_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401, not 403, for an anonymous user on an admin-only route")
        void shouldReturn401ForAnonymousOnAdminRoute() throws Exception {
            expectUnauthorized(mockMvc.perform(get(ADMIN_URL)));
        }
    }

    @Nested
    @DisplayName("Actuator authorization")
    class ActuatorAuthorizationTests {

        @Test
        @DisplayName("Should return 403 as JSON for EMPLOYEE on metrics")
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

        @ParameterizedTest(name = "GET {0}")
        @ValueSource(strings = {
                "/actuator/health/liveness",
                "/actuator/health/readiness"
        })
        @DisplayName("Should allow health probes without a token")
        void shouldAllowHealthProbesWithoutToken(String url) throws Exception {
            MvcResult result = mockMvc.perform(get(url))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();

            assertNotEquals(401, statusCode,
                    "The probe must not require authentication");
            assertNotEquals(403, statusCode,
                    "The probe must not be blocked by authorization");

            verifyNoInteractions(userDetailsService);
        }
    }

    @Nested
    @DisplayName("Correlation ID in the filter chain")
    class CorrelationIdTests {

        @Test
        @DisplayName("Should generate the header when the request does not send it")
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
        @DisplayName("Should echo the received header when it is a valid UUID")
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
        @DisplayName("Should also include the header in 401 responses")
        void shouldIncludeCorrelationIdOnUnauthorizedResponses() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("Should include the header in responses where the JWT filter rejects the token")
        void shouldIncludeCorrelationIdWhenJwtFilterRejectsToken() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(get(PROTECTED_URL)
                            .header(CORRELATION_ID_HEADER, correlationId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertEquals(correlationId, result.getResponse().getHeader(CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("Should keep the filter's automatic servlet registration disabled")
        void shouldKeepServletRegistrationDisabled() {
            assertFalse(correlationIdFilterRegistration.isEnabled());
            assertInstanceOf(CorrelationIdFilter.class, correlationIdFilterRegistration.getFilter());
        }
    }

    @Nested
    @DisplayName("Authentication beans")
    class AuthenticationBeansTests {

        @Test
        @DisplayName("Should use BCrypt as the PasswordEncoder")
        void shouldUseBCryptPasswordEncoder() {
            assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);

            String hash = passwordEncoder.encode(PASSWORD);

            assertTrue(hash.startsWith("$2"), "The hash must be in BCrypt format");
            assertNotEquals(PASSWORD, hash);
            assertTrue(passwordEncoder.matches(PASSWORD, hash));
            assertFalse(passwordEncoder.matches("WrongPassword", hash));
            assertNotEquals(hash, passwordEncoder.encode(PASSWORD), "The salt must vary on each hash");
        }

        @Test
        @DisplayName("Should authenticate with valid credentials")
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
        @DisplayName("Should reject a wrong password")
        void shouldRejectWrongPassword() {
            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(createUserWithEncodedPassword());

            assertThrows(BadCredentialsException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, "WrongPassword")));
        }

        @Test
        @DisplayName("Should treat an unknown user as bad credentials")
        void shouldTreatUnknownUserAsBadCredentials() {
            when(userDetailsService.loadUserByUsername(anyString()))
                    .thenThrow(new UsernameNotFoundException("Usuário ou senha inválidos."));

            assertThrows(BadCredentialsException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated("nonexistent", PASSWORD)));
        }

        @Test
        @DisplayName("Should report a database failure as InternalAuthenticationServiceException, not as invalid credentials")
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
        @DisplayName("Should reject an inactive user even with the correct password")
        void shouldRejectDisabledUserEvenWithCorrectPassword() {
            User user = createUserWithEncodedPassword();
            user.deactivate();

            assertFalse(user.isEnabled());

            when(userDetailsService.loadUserByUsername(USERNAME))
                    .thenReturn(user);

            assertThrows(DisabledException.class, () ->
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    USERNAME, PASSWORD
                            )
                    ));

            verify(userDetailsService).loadUserByUsername(USERNAME);
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

    /** Builds a signed token without the required "tv" claim. */
    private static String tokenWithoutVersion() {
        return Jwts.builder()
                .subject(USERNAME)
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private static JwtService serviceOf(
            String secret,
            long expirationInSeconds,
            String issuer
    ) {
        return JwtService.create(
                new JwtProperties(secret, expirationInSeconds, issuer),
                Clock.systemUTC()
        );
    }

    private static User createUser(Role role) {
        return UserTestFactory.aUser()
                .withUsername(USERNAME)
                .withRole(role)
                .build();
    }

    private static User createUser(String encodedPassword) {
        return UserTestFactory.aUser()
                .withUsername(USERNAME)
                .withEncodedPassword(encodedPassword)
                .withRole(Role.EMPLOYEE)
                .build();
    }

    private User createUserWithEncodedPassword() {
        return createUser(passwordEncoder.encode(PASSWORD));
    }
}
