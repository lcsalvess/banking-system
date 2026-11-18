package com.lcsalvess.bankingsystem.security.handler;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles authentication failures for REST endpoints using standardized JSON responses.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final JsonMapper jsonMapper;
  private final Clock clock;

  public RestAuthenticationEntryPoint(JsonMapper jsonMapper, Clock clock) {
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public void commence(
      @NonNull HttpServletRequest request,
      HttpServletResponse response,
      @NonNull AuthenticationException exception
  ) throws IOException {

    final ApiErrorResponse errorResponse = ApiErrorResponse.of(
        clock,
        HttpServletResponse.SC_UNAUTHORIZED,
        ApiErrorMessages.AUTHENTICATION_FAILED
    );

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    response.getWriter().write(
        jsonMapper.writeValueAsString(errorResponse)
    );
  }

  /**
   * Returns a JSON 503 response when authentication cannot be completed because an
   * infrastructure dependency is unavailable, avoiding a misleading 401 response.
   *
   * @param response HTTP response to populate
   * @throws IOException if writing the response fails
   */
  public void commenceServiceUnavailable(
      HttpServletResponse response
  ) throws IOException {

    final ApiErrorResponse errorResponse = ApiErrorResponse.of(
        clock,
        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
        ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
    );

    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    response.getWriter().write(
        jsonMapper.writeValueAsString(errorResponse)
    );
  }
}
