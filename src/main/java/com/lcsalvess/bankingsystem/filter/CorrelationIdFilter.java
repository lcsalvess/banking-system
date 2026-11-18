package com.lcsalvess.bankingsystem.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation identifier to each HTTP request for request tracing.
 * <p>
 * Reuses a valid correlation ID from the request header or generates a new UUID
 * when the header is missing or contains an invalid value. The identifier is
 * added to the response header and the logging MDC during request processing.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";

  /**
   * Adds the correlation ID to the logging context and HTTP response.
   * <p>
   * The MDC entry is removed after request processing to prevent context
   * leakage between requests handled by the same thread.
   *
   * @param request the incoming HTTP request
   * @param response the HTTP response to receive the correlation ID header
   * @param filterChain the filter chain to continue request processing
   * @throws ServletException if request processing fails
   * @throws IOException if an input or output operation fails
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    String correlationId = resolveCorrelationId(request);

    MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    String correlationId = request.getHeader(CORRELATION_ID_HEADER);

    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }

    try {
      UUID uuid = UUID.fromString(correlationId);

      if (!uuid.toString().equalsIgnoreCase(correlationId)) {
        return UUID.randomUUID().toString();
      }

      return uuid.toString();
    } catch (IllegalArgumentException exception) {
      return UUID.randomUUID().toString();
    }
  }
}
