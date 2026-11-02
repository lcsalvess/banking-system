package com.lcsalvess.bankingsystem.unit.security;

import com.lcsalvess.bankingsystem.security.AuditExceptionCaptureResolver;
import com.lcsalvess.bankingsystem.security.AuditRequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("AuditExceptionCaptureResolver")
class AuditExceptionCaptureResolverTests {

    private final AuditExceptionCaptureResolver resolver =
            new AuditExceptionCaptureResolver();

    @Test
    @DisplayName("Should store the exception on the request and let other resolvers answer")
    void shouldStoreExceptionAndDeclineToResolve() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        IllegalStateException exception = new IllegalStateException("boom");

        assertNull(resolver.resolveException(
                request,
                new MockHttpServletResponse(),
                null,
                exception
        ));

        assertSame(
                exception,
                request.getAttribute(AuditRequestAttributes.FAILURE_EXCEPTION)
        );
    }

    @Test
    @DisplayName("Should run before every other resolver")
    void shouldHaveHighestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, resolver.getOrder());
    }
}
