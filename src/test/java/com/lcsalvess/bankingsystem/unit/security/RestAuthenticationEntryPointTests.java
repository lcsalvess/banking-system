package com.lcsalvess.bankingsystem.unit.security;

import com.lcsalvess.bankingsystem.dto.response.exception.ErrorResponse;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RestAuthenticationEntryPointTests {

    private static final int UNAUTHORIZED_STATUS =
            HttpServletResponse.SC_UNAUTHORIZED;

    private static final String EXPECTED_MESSAGE =
            "Não foi possível autenticar o usuário.";

    private JsonMapper jsonMapper;
    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        authenticationEntryPoint = new RestAuthenticationEntryPoint(jsonMapper);

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts");

        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("Authentication failure response")
    class AuthenticationFailureResponse {

        @Test
        @DisplayName("Should return a JSON 401 response with the expected error")
        void shouldReturnJsonUnauthorizedResponseWithExpectedError()
                throws Exception {

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid credentials")
            );

            ErrorResponse errorResponse = readErrorResponse();

            assertUnauthorizedResponse(errorResponse);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("authenticationExceptions")
        @DisplayName("Should return the same response for authentication exception types")
        void shouldReturnSameResponseForAuthenticationExceptionTypes(
                String scenario,
                AuthenticationException exception
        ) throws Exception {

            authenticationEntryPoint.commence(
                    request,
                    response,
                    exception
            );

            ErrorResponse errorResponse = readErrorResponse();

            assertUnauthorizedResponse(errorResponse);
        }

        static Stream<Arguments> authenticationExceptions() {
            return Stream.of(
                    Arguments.of(
                            "bad credentials",
                            new BadCredentialsException(
                                    "Invalid username or password"
                            )
                    ),
                    Arguments.of(
                            "credentials not found",
                            new AuthenticationCredentialsNotFoundException(
                                    "Authentication required"
                            )
                    ),
                    Arguments.of(
                            "insufficient authentication",
                            new InsufficientAuthenticationException(
                                    "Insufficient authentication"
                            )
                    )
            );
        }
    }

    @Nested
    @DisplayName("UTF-8 encoding")
    class Utf8Encoding {

        @Test
        @DisplayName("Should preserve accented characters when response is decoded as UTF-8")
        void shouldPreserveAccentedCharactersWhenDecodedAsUtf8()
                throws Exception {

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid credentials")
            );

            byte[] responseBytes = response.getContentAsByteArray();

            String body = new String(
                    responseBytes,
                    StandardCharsets.UTF_8
            );

            assertAll(
                    () -> assertEquals(
                            StandardCharsets.UTF_8.name(),
                            response.getCharacterEncoding()
                    ),
                    () -> assertTrue(
                            body.contains(EXPECTED_MESSAGE)
                    ),
                    () -> assertFalse(
                            body.contains("NÃ£o")
                    ),
                    () -> assertFalse(
                            body.contains("usuÃ¡rio")
                    )
            );
        }
    }

    @Nested
    @DisplayName("Response body")
    class ResponseBody {

        @Test
        @DisplayName("Should not expose authentication exception details")
        void shouldNotExposeAuthenticationExceptionDetails()
                throws Exception {

            AuthenticationException exception =
                    new BadCredentialsException(
                            "Sensitive authentication failure details"
                    );

            authenticationEntryPoint.commence(
                    request,
                    response,
                    exception
            );

            String body = response.getContentAsString(
                    StandardCharsets.UTF_8
            );

            assertAll(
                    () -> assertFalse(
                            body.contains(
                                    exception.getClass().getSimpleName()
                            )
                    ),
                    () -> assertFalse(
                            body.contains(exception.getMessage())
                    ),
                    () -> assertEquals(
                            EXPECTED_MESSAGE,
                            readErrorResponse().message()
                    )
            );
        }
    }

    private void assertUnauthorizedResponse(ErrorResponse errorResponse) {
        MediaType contentType = MediaType.parseMediaType(
                response.getContentType()
        );

        assertAll(
                () -> assertEquals(
                        UNAUTHORIZED_STATUS,
                        response.getStatus()
                ),
                () -> assertTrue(
                        contentType.isCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                ),
                () -> assertEquals(
                        StandardCharsets.UTF_8.name(),
                        response.getCharacterEncoding()
                ),
                () -> assertEquals(
                        UNAUTHORIZED_STATUS,
                        errorResponse.status()
                ),
                () -> assertEquals(
                        EXPECTED_MESSAGE,
                        errorResponse.message()
                ),
                () -> assertNotNull(
                        errorResponse.timestamp()
                )
        );
    }

    private ErrorResponse readErrorResponse() throws Exception {
        return jsonMapper.readValue(
                response.getContentAsString(StandardCharsets.UTF_8),
                ErrorResponse.class
        );
    }
}