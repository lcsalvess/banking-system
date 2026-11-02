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

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Records audit events for authenticated requests whose outcomes are not
 * captured by the business-operation audit records, including access denials,
 * failures of data-modifying operations, and server errors.
 *
 * <p>Registered as a servlet filter outside the Spring Security filter chain,
 * it wraps downstream request processing. After the filter chain returns,
 * it inspects the HTTP response status and request attributes populated by
 * authentication and MVC exception handling to determine whether an audit
 * event should be recorded.
 *
 * <p>Requests without an authenticated application user are ignored by this
 * filter. Authentication events, including login successes, failures, and
 * rate-limit blocks, are recorded by the authentication flow.
 */
public class AuditTrailFilter extends OncePerRequestFilter {

    private static final Set<String> READ_ONLY_METHODS =
            Set.of("GET", "HEAD", "OPTIONS");

    private static final Pattern CAMEL_BOUNDARY =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

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

        if (!(request.getAttribute(AuditRequestAttributes.AUTHENTICATED_USER)
                instanceof User actor)) {
            return;
        }

        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String clientIp = request.getRemoteAddr();
        UUID correlationId = correlationIdOf(response);
        Object failure =
                request.getAttribute(AuditRequestAttributes.FAILURE_EXCEPTION);

        if (status == HttpServletResponse.SC_FORBIDDEN) {
            auditTrailRecorder.recordAccessDenied(
                    actor, method, path, clientIp, correlationId
            );
        } else if (status >= 500) {
            auditTrailRecorder.recordOperationFailed(
                    actor, method, path, clientIp, status,
                    reasonOf(failure), correlationId
            );
        } else if (status >= 400
                && !READ_ONLY_METHODS.contains(method)
                && failure instanceof BusinessException) {
            auditTrailRecorder.recordOperationFailed(
                    actor, method, path, clientIp, status,
                    reasonOf(failure), correlationId
            );
        }
    }

    /**
     * Código estável do motivo. Para erros de negócio vem do nome da classe
     * ({@code InsufficientBalanceException} vira
     * {@code INSUFFICIENT_BALANCE}); para o resto, um código fixo, para nunca
     * gravar nome de classe interna nem mensagem de exceção.
     */
    static String reasonOf(Object failure) {
        if (failure instanceof BusinessException) {
            String name = failure.getClass().getSimpleName();

            if (name.endsWith("Exception")) {
                name = name.substring(0, name.length() - "Exception".length());
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
     * O MDC já foi limpo pelo {@code CorrelationIdFilter} quando este filtro
     * grava; o id continua disponível no cabeçalho da resposta.
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
