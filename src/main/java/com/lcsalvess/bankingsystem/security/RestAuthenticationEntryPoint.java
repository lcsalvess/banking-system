package com.lcsalvess.bankingsystem.security;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "JsonMapper is a Spring-managed collaborator intentionally retained for JSON serialization."
    )
    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws IOException {

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED,
                ApiErrorMessages.AUTHENTICATION_FAILED
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);

        response.getWriter().write(
                jsonMapper.writeValueAsString(errorResponse)
        );
    }

    /**
     * Responde 503 em JSON quando a autenticação não pôde ser concluída por falha de
     * infraestrutura (por exemplo, banco indisponível), em vez de um 401 enganoso.
     */
    public void commenceServiceUnavailable(
            HttpServletResponse response
    ) throws IOException {

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
        );

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);

        response.getWriter().write(
                jsonMapper.writeValueAsString(errorResponse)
        );
    }
}