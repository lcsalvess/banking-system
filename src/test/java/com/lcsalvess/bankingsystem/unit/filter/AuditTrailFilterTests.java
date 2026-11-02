package com.lcsalvess.bankingsystem.unit.filter;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.transaction.InsufficientBalanceException;
import com.lcsalvess.bankingsystem.filter.AuditTrailFilter;
import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.security.AuditRequestAttributes;
import com.lcsalvess.bankingsystem.service.audit.AuditTrailRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditTrailFilter")
class AuditTrailFilterTests {

    private static final String PATH =
            "/api/v1/transactions/withdraw";

    private static final String IP = "192.0.2.10";

    private static final String LOGIN_PATH =
            "/api/v1/auth/login";

    @Mock
    private AuditTrailRecorder recorder;

    private AuditTrailFilter filter;
    private User actor;

    @BeforeEach
    void setUp() {
        filter = new AuditTrailFilter(recorder);

        actor = new User(
                "lucas",
                "lucas@example.com",
                "hash",
                Role.EMPLOYEE
        );

        ReflectionTestUtils.setField(actor, "id", 7L);
    }

    private MockHttpServletRequest request(
            String method,
            boolean authenticated
    ) {
        return request(method, PATH, authenticated);
    }

    private MockHttpServletRequest request(
            String method,
            String path,
            boolean authenticated
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);

        request.setRemoteAddr(IP);

        if (authenticated) {
            request.setAttribute(
                    AuditRequestAttributes.AUTHENTICATED_USER,
                    actor
            );
        }

        return request;
    }

    private MockHttpServletResponse run(
            MockHttpServletRequest request,
            int status,
            Exception failure
    ) throws Exception {
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(status);

            if (failure != null) {
                req.setAttribute(
                        AuditRequestAttributes.FAILURE_EXCEPTION,
                        failure
                );
            }
        };

        filter.doFilter(request, response, chain);

        return response;
    }

    @Test
    @DisplayName("Should record an access denied as an HTTP outcome")
    void shouldRecordAccessDenied() throws Exception {
        run(request("GET", true), 403, null);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq(PATH),
                eq(IP),
                eq(403),
                eq("FORBIDDEN"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should take the correlation id from the response header")
    void shouldUseCorrelationIdFromResponseHeader() throws Exception {
        UUID correlationId = UUID.randomUUID();

        MockHttpServletRequest request = request("GET", true);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setHeader(
                    CorrelationIdFilter.CORRELATION_ID_HEADER,
                    correlationId.toString()
            );

            ((HttpServletResponse) res).setStatus(403);
        };

        filter.doFilter(request, response, chain);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq(PATH),
                eq(IP),
                eq(403),
                eq("FORBIDDEN"),
                eq(correlationId)
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should ignore a correlation id header that is not a UUID")
    void shouldIgnoreMalformedCorrelationId() throws Exception {
        MockHttpServletRequest request = request("GET", true);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setHeader(
                    CorrelationIdFilter.CORRELATION_ID_HEADER,
                    "not-a-uuid"
            );

            ((HttpServletResponse) res).setStatus(403);
        };

        filter.doFilter(request, response, chain);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq(PATH),
                eq(IP),
                eq(403),
                eq("FORBIDDEN"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a business failure with its specific reason")
    void shouldRecordBusinessFailure() throws Exception {
        run(
                request("POST", true),
                409,
                new InsufficientBalanceException("insufficient")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(409),
                eq("INSUFFICIENT_BALANCE"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a failure on a read-only request")
    void shouldRecordBusinessFailureOnRead() throws Exception {
        run(
                request("GET", true),
                409,
                new InsufficientBalanceException("insufficient")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq(PATH),
                eq(IP),
                eq(409),
                eq("INSUFFICIENT_BALANCE"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a server error with a fixed internal code")
    void shouldRecordUnexpectedServerError() throws Exception {
        run(
                request("POST", true),
                500,
                new IllegalStateException("secret detail")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(500),
                eq("INTERNAL_ERROR"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a lock failure with its specific reason")
    void shouldRecordLockFailureOnRead() throws Exception {
        run(
                request("GET", true),
                503,
                new PessimisticLockingFailureException("lock")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq(PATH),
                eq(IP),
                eq(503),
                eq("DATABASE_LOCK_FAILURE"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @ParameterizedTest(name = "Should classify {0} as {1}")
    @MethodSource("databaseFailureCases")
    @DisplayName("Should distinguish database timeouts from database unavailability")
    void shouldClassifyDatabaseFailuresPrecisely(
            Exception failure,
            String expectedReason
    ) throws Exception {
        run(
                request("POST", true),
                503,
                failure
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(503),
                eq(expectedReason),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    private static Stream<Arguments> databaseFailureCases() {
        return Stream.of(
                Arguments.of(
                        new QueryTimeoutException("query timeout"),
                        "DATABASE_QUERY_TIMEOUT"
                ),
                Arguments.of(
                        new DataAccessResourceFailureException(
                                "resource unavailable"
                        ),
                        "DATABASE_UNAVAILABLE"
                ),
                Arguments.of(
                        new CannotCreateTransactionException(
                                "transaction unavailable"
                        ),
                        "DATABASE_UNAVAILABLE"
                )
        );
    }

    @Test
    @DisplayName("Should map data integrity and provider failures to stable codes")
    void shouldMapInfrastructureFailures() throws Exception {
        run(
                request("POST", true),
                500,
                new DataIntegrityViolationException("x")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(500),
                eq("DATA_INTEGRITY_VIOLATION"),
                isNull()
        );

        run(
                request("POST", true),
                503,
                new AddressProviderUnavailableException("down")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(503),
                eq("EXTERNAL_PROVIDER_UNAVAILABLE"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should use a generic server error reason when no exception was captured")
    void shouldUseGenericServerErrorWithoutException() throws Exception {
        run(request("POST", true), 500, null);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(500),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should use a generic client error reason for validation failures")
    void shouldRecordValidationErrors() throws Exception {
        run(
                request("POST", true),
                400,
                new IllegalArgumentException("bad")
        );

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(400),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should ignore anonymous requests outside the login endpoint")
    void shouldIgnoreAnonymousRequests() throws Exception {
        run(request("POST", false), 401, null);
        run(request("POST", false), 403, null);
        run(request("POST", false), 500, null);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a successful state-changing request")
    void shouldRecordSuccessfulStateChangingRequest() throws Exception {
        run(request("POST", true), 201, null);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(201),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should ignore successful read-only requests")
    void shouldIgnoreSuccessfulReadOnlyRequest() throws Exception {
        run(request("GET", true), 200, null);

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a successful anonymous login response")
    void shouldRecordSuccessfulLogin() throws Exception {
        run(
                request("POST", LOGIN_PATH, false),
                200,
                null
        );

        verify(recorder).recordHttpRequestCompleted(
                isNull(),
                eq("POST"),
                eq(LOGIN_PATH),
                eq(IP),
                eq(200),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should record a failed anonymous login response")
    void shouldRecordFailedLogin() throws Exception {
        run(
                request("POST", LOGIN_PATH, false),
                401,
                null
        );

        verify(recorder).recordHttpRequestCompleted(
                isNull(),
                eq("POST"),
                eq(LOGIN_PATH),
                eq(IP),
                eq(401),
                eq("UNAUTHORIZED"),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should not duplicate rate-limited login audit events")
    void shouldNotRecordRateLimitedLogin() throws Exception {
        run(
                request("POST", LOGIN_PATH, false),
                429,
                null
        );

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Should use the MVC route pattern when available")
    void shouldUseMvcRoutePattern() throws Exception {
        MockHttpServletRequest request = request(
                "GET",
                "/api/v1/clients/12345",
                true
        );

        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/clients/{id}"
        );

        run(request, 404, null);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq("/api/v1/clients/{id}"),
                eq(IP),
                eq(404),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should mask numeric identifiers when the MVC route pattern is unavailable")
    void shouldMaskNumericPathSegments() throws Exception {
        MockHttpServletRequest request = request(
                "GET",
                "/api/v1/clients/12345",
                true
        );

        run(request, 404, null);

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("GET"),
                eq("/api/v1/clients/{id}"),
                eq(IP),
                eq(404),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }

    @Test
    @DisplayName("Should ignore requests outside the API")
    void shouldIgnoreRequestsOutsideApi() throws Exception {
        run(
                request("POST", "/actuator/health", true),
                500,
                null
        );

        verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("Should still hand the request to the rest of the chain")
    void shouldContinueTheChain() throws Exception {
        MockHttpServletResponse response =
                run(request("POST", true), 201, null);

        assertEquals(201, response.getStatus());

        verify(recorder).recordHttpRequestCompleted(
                eq(actor),
                eq("POST"),
                eq(PATH),
                eq(IP),
                eq(201),
                isNull(),
                isNull()
        );

        verifyNoMoreInteractions(recorder);
    }
}
