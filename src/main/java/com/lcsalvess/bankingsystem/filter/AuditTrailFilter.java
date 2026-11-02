package com.lcsalvess.bankingsystem.filter;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.security.AuditRequestAttributes;
import com.lcsalvess.bankingsystem.service.audit.AuditTrailRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records the final HTTP outcome of relevant API requests.
 *
 * <p>Business events remain recorded transactionally by AuditService.
 * This filter complements them with the actual HTTP response returned
 * by the API.
 *
 * <p>Successful state-changing requests, authenticated request failures,
 * and login requests are recorded. Successful read-only requests and
 * anonymous rejections outside the login endpoint are ignored.
 *
 * <p>The audit write is best-effort and must never change the original
 * HTTP response.
 */
public class AuditTrailFilter extends OncePerRequestFilter {

    private static final String API_BASE_PATH = "/api/v1";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final Set<String> STATE_CHANGING_METHODS =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Pattern CAMEL_BOUNDARY =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)"
    );

    private static final Pattern NUMERIC_PATH_SEGMENT =
            Pattern.compile("(?<=/)\\d+(?=/|$)");

    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final AuditTrailRecorder auditTrailRecorder;

    public AuditTrailFilter(AuditTrailRecorder auditTrailRecorder) {
        this.auditTrailRecorder = auditTrailRecorder;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        String method = request.getMethod();
        String path = endpointPath(request);

        if (!isApiPath(path)) {
            return;
        }

        User actor = request.getAttribute(
                AuditRequestAttributes.AUTHENTICATED_USER
        ) instanceof User authenticatedUser
                ? authenticatedUser
                : null;

        int status = response.getStatus();

        boolean loginRequest = "POST".equals(method)
                && LOGIN_PATH.equals(path);

        /*
         * LOGIN_BLOCKED is already deduplicated by AuditTrailRecorder.
         * Recording every HTTP 429 here would bypass that protection.
         */
        if (loginRequest && status == 429) {
            return;
        }

        boolean stateChangingRequest =
                STATE_CHANGING_METHODS.contains(method);

        boolean failedRequest = status >= 400;

        /*
         * Anonymous requests are only recorded for the login endpoint.
         * This avoids allowing unauthenticated requests to flood the
         * append-only audit table.
         */
        if (!loginRequest
                && (actor == null
                || (!stateChangingRequest && !failedRequest))) {
            return;
        }

        Object failure = request.getAttribute(
                AuditRequestAttributes.FAILURE_EXCEPTION
        );

        String failureReason = status >= 400
                ? failureReasonOf(status, failure)
                : null;

        auditTrailRecorder.recordHttpRequestCompleted(
                actor,
                method,
                path,
                request.getRemoteAddr(),
                status,
                failureReason,
                correlationIdOf(response)
        );
    }

    private static boolean isApiPath(String path) {
        return path.equals(API_BASE_PATH)
                || path.startsWith(API_BASE_PATH + "/");
    }

    /**
     * Uses the MVC route pattern when available. For requests rejected
     * before MVC dispatch, falls back to the request URI while masking
     * numeric and UUID path segments.
     */
    private static String endpointPath(HttpServletRequest request) {
        Object matchingPattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );

        if (matchingPattern != null) {
            return matchingPattern.toString();
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null
                && !contextPath.isEmpty()
                && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        path = UUID_PATH_SEGMENT.matcher(path)
                .replaceAll("{id}");

        return NUMERIC_PATH_SEGMENT.matcher(path)
                .replaceAll("{id}");
    }

    /**
     * Maps known HTTP outcomes to stable reason codes.
     * Business exceptions retain their specific codes.
     */
    private static String failureReasonOf(
            int httpStatus,
            Object failure
    ) {
        if (httpStatus == HttpServletResponse.SC_UNAUTHORIZED) {
            return "UNAUTHORIZED";
        }

        if (httpStatus == HttpServletResponse.SC_FORBIDDEN) {
            return "FORBIDDEN";
        }

        if (httpStatus == 429) {
            return "RATE_LIMIT_EXCEEDED";
        }

        if (failure instanceof BusinessException
                || failure instanceof DataIntegrityViolationException
                || failure instanceof PessimisticLockingFailureException
                || failure instanceof QueryTimeoutException
                || failure instanceof DataAccessResourceFailureException
                || failure instanceof CannotCreateTransactionException
                || failure instanceof AddressProviderUnavailableException) {
            return reasonOf(failure);
        }

        /*
         * Preserve a specific internal failure code for server errors.
         * Unknown client errors use the generic code from AuditTrailRecorder.
         */
        if (httpStatus >= 500 && failure != null) {
            return reasonOf(failure);
        }

        return null;
    }

    /**
     * Converts known exception types into stable reason codes.
     * Exception messages and internal class names are never persisted.
     */
    static String reasonOf(Object failure) {
        if (failure instanceof BusinessException) {
            String name = failure.getClass().getSimpleName();

            if (name.endsWith("Exception")) {
                name = name.substring(
                        0,
                        name.length() - "Exception".length()
                );
            }

            String code = CAMEL_BOUNDARY.matcher(name)
                    .replaceAll("_")
                    .toUpperCase(Locale.ROOT);

            return code.isEmpty() ? INTERNAL_ERROR : code;
        }

        if (failure instanceof DataIntegrityViolationException) {
            return "DATA_INTEGRITY_VIOLATION";
        }

        if (failure instanceof PessimisticLockingFailureException) {
            return "DATABASE_LOCK_FAILURE";
        }

        if (failure instanceof QueryTimeoutException) {
            return "DATABASE_QUERY_TIMEOUT";
        }

        if (failure instanceof DataAccessResourceFailureException
                || failure instanceof CannotCreateTransactionException) {
            return "DATABASE_UNAVAILABLE";
        }

        if (failure instanceof AddressProviderUnavailableException) {
            return "EXTERNAL_PROVIDER_UNAVAILABLE";
        }

        return INTERNAL_ERROR;
    }

    /**
     * The CorrelationIdFilter clears the MDC before this filter records.
     * The response header remains available and carries the request ID.
     */
    private static UUID correlationIdOf(HttpServletResponse response) {
        String header = response.getHeader(
                CorrelationIdFilter.CORRELATION_ID_HEADER
        );

        if (header == null) {
            return null;
        }

        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
