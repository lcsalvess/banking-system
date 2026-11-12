package com.lcsalvess.bankingsystem.filter;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.security.AuditRequestAttributes;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditTrailRecorder;
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

    private static final Set<String> AUDITED_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"
    );

    /*
     * Routes known to the API. Used only when a request was rejected before
     * MVC dispatch (no route pattern available), so that client-controlled
     * path text never reaches the append-only audit table. Keep in sync with
     * SecurityConfig; an unlisted literal degrades safely to {id}.
     */
    private static final Set<String> KNOWN_RESOURCES = Set.of(
            "auth", "users", "clients", "accounts", "transactions",
            "audit-events"
    );

    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "login", "password",
            "deposits", "withdrawals", "transfers",
            "code", "accounts", "transactions",
            "deactivate", "reactivate", "role"
    );

    private static final int MAX_UNMATCHED_SEGMENTS = 4;

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

        Throwable escaped = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException
                 | Error exception) {
            escaped = exception;
            throw exception;
        } finally {
            recordOutcome(request, response, escaped);
        }
    }

    /**
     * Runs even when the chain throws. Never propagates: the audit write
     * is best-effort and must not replace the original outcome.
     */
    private void recordOutcome(
            HttpServletRequest request,
            HttpServletResponse response,
            Throwable escaped
    ) {
        try {
            String method = methodOf(request);
            String path = endpointPath(request);

            if (isOutsideApi(path)) {
                return;
            }

            User actor = request.getAttribute(
                    AuditRequestAttributes.AUTHENTICATED_USER
            ) instanceof User authenticatedUser
                    ? authenticatedUser
                    : null;

            // An exception that escaped the chain has no response status yet:
            // the container will answer 500.
            int status = escaped != null && response.getStatus() < 400
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();

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
             * Repeated client errors are additionally deduplicated by
             * AuditTrailRecorder, so neither anonymous nor authenticated
             * clients can control the growth of the append-only table.
             */
            if (!loginRequest
                    && (actor == null
                    || (!stateChangingRequest && !failedRequest))) {
                return;
            }

            Object failure = request.getAttribute(
                    AuditRequestAttributes.FAILURE_EXCEPTION
            );

            if (failure == null) {
                failure = escaped;
            }

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
        } catch (RuntimeException exception) {
            logger.warn("Could not record the HTTP outcome in the audit trail",
                    exception);
        }
    }

    /**
     * Unknown methods are collapsed so the method cannot be used to
     * create unbounded distinct audit subjects.
     */
    private static String methodOf(HttpServletRequest request) {
        String method = request.getMethod();

        return method != null && AUDITED_METHODS.contains(method)
                ? method
                : "OTHER";
    }

    private static boolean isOutsideApi(String path) {
        return !path.equals(API_BASE_PATH)
                && !path.startsWith(API_BASE_PATH + "/");
    }

    /**
     * Uses the MVC route pattern when available. For requests rejected
     * before MVC dispatch, falls back to the request URI reduced to known
     * route segments (see {@link #sanitizeUnmatchedPath}).
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

        return sanitizeUnmatchedPath(path);
    }

    /**
     * Keeps only the API base path, a known resource, and known action
     * literals. Every other segment becomes {id}; an unknown resource
     * collapses the whole path to {unmatched}. The result has bounded
     * cardinality and never contains client-chosen text or identifiers.
     */
    private static String sanitizeUnmatchedPath(String path) {
        if (isOutsideApi(path)) {
            return path;
        }

        StringBuilder sanitized = new StringBuilder(API_BASE_PATH);
        int kept = 0;

        for (String segment : path.substring(API_BASE_PATH.length()).split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }

            if (kept == MAX_UNMATCHED_SEGMENTS) {
                break;
            }

            if (kept == 0) {
                if (!KNOWN_RESOURCES.contains(segment)) {
                    return API_BASE_PATH + "/{unmatched}";
                }

                sanitized.append('/').append(segment);
            } else {
                sanitized.append('/').append(
                        KNOWN_ACTIONS.contains(segment) ? segment : "{id}"
                );
            }

            kept++;
        }

        return sanitized.toString();
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
