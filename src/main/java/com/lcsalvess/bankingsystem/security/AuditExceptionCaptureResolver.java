package com.lcsalvess.bankingsystem.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Only stores the exception being handled by Spring MVC as a request
 * attribute and returns {@code null}, allowing {@code GlobalExceptionHandler}
 * to generate the response. It uses the same mechanism as Spring Boot's
 * {@code DefaultErrorAttributes} and allows the audit filter to determine
 * the cause of the failure without modifying any exception handler.
 */
public class AuditExceptionCaptureResolver
        implements HandlerExceptionResolver, Ordered {

    @Override
    public @Nullable ModelAndView resolveException(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @Nullable Object handler,
            @NonNull Exception exception
    ) {
        request.setAttribute(
                AuditRequestAttributes.FAILURE_EXCEPTION,
                exception
        );

        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
