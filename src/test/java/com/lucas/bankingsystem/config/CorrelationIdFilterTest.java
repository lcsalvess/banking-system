package com.lucas.bankingsystem.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private CorrelationIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    class GenerateCorrelationId {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidOrMissingHeaders")
        void shouldGenerateCorrelationIdWhenHeaderIsMissingOrBlank(
                String scenario,
                String header
        ) throws ServletException, IOException {

            when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(header);

            String correlationId = captureMdcCorrelationId();

            assertNotNull(correlationId);
            assertDoesNotThrow(() -> UUID.fromString(correlationId));

            verify(response).setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        static Stream<Arguments> invalidOrMissingHeaders() {
            return Stream.of(
                    Arguments.of("header is absent", null),
                    Arguments.of("header is empty", ""),
                    Arguments.of("header is blank", "   ")
            );
        }
    }

    @Nested
    class ReuseCorrelationId {

        @Test
        void shouldReuseValidCorrelationId() throws ServletException, IOException {
            String correlationId = UUID.randomUUID().toString();

            when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);

            String actualCorrelationId = captureMdcCorrelationId();

            assertEquals(correlationId, actualCorrelationId);

            verify(response).setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        @Test
        void shouldNormalizeUppercaseCorrelationId() throws ServletException, IOException {
            String correlationId = "550E8400-E29B-41D4-A716-446655440000";
            String expectedCorrelationId = correlationId.toLowerCase();

            when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);

            String actualCorrelationId = captureMdcCorrelationId();

            assertEquals(expectedCorrelationId, actualCorrelationId);

            verify(response).setHeader(
                    CORRELATION_ID_HEADER,
                    expectedCorrelationId
            );
        }
    }

    @Nested
    class InvalidCorrelationId {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidCorrelationIds")
        void shouldGenerateNewCorrelationIdWhenHeaderIsInvalid(
                String scenario,
                String invalidCorrelationId
        ) throws ServletException, IOException {

            when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(invalidCorrelationId);

            String correlationId = captureMdcCorrelationId();

            assertNotNull(correlationId);
            assertNotEquals(invalidCorrelationId, correlationId);
            assertDoesNotThrow(() -> UUID.fromString(correlationId));

            verify(response).setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        static Stream<Arguments> invalidCorrelationIds() {
            return Stream.of(
                    Arguments.of("random text", "abc"),
                    Arguments.of(
                            "invalid UUID",
                            "550e8400-e29b-41d4-a716-446655440000-invalid"
                    ),
                    Arguments.of(
                            "malformed UUID",
                            "550e8400-e29b-41d4-a716"
                    ),
                    Arguments.of(
                            "non-canonical UUID",
                            "1-1-1-1-1"
                    )
            );
        }
    }

    @Nested
    class MdcCleanup {

        @Test
        void shouldRemoveCorrelationIdFromMdcAfterRequest()
                throws ServletException, IOException {

            String correlationId = UUID.randomUUID().toString();

            when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);

            doAnswer(invocation -> {
                assertEquals(
                        correlationId,
                        MDC.get(CORRELATION_ID_MDC_KEY)
                );
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertNull(MDC.get(CORRELATION_ID_MDC_KEY));
        }
    }

    private String captureMdcCorrelationId()
            throws ServletException, IOException {

        final String[] correlationId = new String[1];

        doAnswer(invocation -> {
            correlationId[0] = MDC.get(CORRELATION_ID_MDC_KEY);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        return correlationId[0];
    }
}
