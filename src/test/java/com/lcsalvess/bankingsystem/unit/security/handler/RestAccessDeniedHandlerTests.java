package com.lcsalvess.bankingsystem.unit.security.handler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.security.handler.RestAccessDeniedHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("RestAccessDeniedHandler")
class RestAccessDeniedHandlerTests {

  private static final int FORBIDDEN_STATUS =
      HttpServletResponse.SC_FORBIDDEN;

  private static final Instant FIXED_INSTANT =
      Instant.parse("2026-01-01T12:00:00Z");

  private static final Clock FIXED_CLOCK =
      Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  private static final String REQUEST_URI =
      "/api/v1/admin/users";

  private static final String USERNAME =
      "test.employee";

  private JsonMapper jsonMapper;
  private RestAccessDeniedHandler accessDeniedHandler;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();

    jsonMapper = JsonMapper.builder().build();

    accessDeniedHandler = new RestAccessDeniedHandler(
        jsonMapper,
        FIXED_CLOCK
    );

    request = new MockHttpServletRequest();
    request.setRequestURI(REQUEST_URI);

    response = new MockHttpServletResponse();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("Authentication identification")
  class AuthenticationIdentificationTests {

    @Test
    @DisplayName("Should handle access denial when authentication is absent")
    void shouldHandleAccessDenialWhenAuthenticationIsAbsent()
        throws Exception {

      SecurityContextHolder.clearContext();

      handleAccessDenied();

      assertForbiddenResponse();
    }

    @Test
    @DisplayName("Should handle access denial when authentication is not authenticated")
    void shouldHandleAccessDenialWhenAuthenticationIsNotAuthenticated()
        throws Exception {

      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(
              USERNAME,
              "credentials"
          )
      );

      handleAccessDenied();

      assertForbiddenResponse();
    }

    @Test
    @DisplayName("Should handle access denial when authentication is anonymous")
    void shouldHandleAccessDenialWhenAuthenticationIsAnonymous()
        throws Exception {

      SecurityContextHolder.getContext().setAuthentication(
          new AnonymousAuthenticationToken(
              "test-key",
              "anonymousUser",
              List.of(
                  new SimpleGrantedAuthority("ROLE_ANONYMOUS")
              )
          )
      );

      handleAccessDenied();

      assertForbiddenResponse();
    }

    @Test
    @DisplayName("Should handle access denial when an authenticated user is present")
    void shouldHandleAccessDenialWhenAuthenticatedUserIsPresent()
        throws Exception {

      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(
              USERNAME,
              null,
              List.of(
                  new SimpleGrantedAuthority("ROLE_EMPLOYEE")
              )
          )
      );

      handleAccessDenied();

      assertForbiddenResponse();
    }
  }

  @Nested
  @DisplayName("Response body")
  class ResponseBodyTests {

    @Test
    @DisplayName("Should return a JSON 403 response with the expected error")
    void shouldReturnJsonForbiddenResponseWithExpectedError()
        throws Exception {

      handleAccessDenied();

      ApiErrorResponse errorResponse = readErrorResponse();

      assertForbiddenResponse();

      assertAll(
          () -> assertEquals(
              FORBIDDEN_STATUS,
              errorResponse.status()
          ),
          () -> assertEquals(
              ApiErrorMessages.ACCESS_DENIED_RESOURCE,
              errorResponse.message()
          ),
          () -> assertEquals(
              FIXED_INSTANT,
              errorResponse.timestamp()
          )
      );
    }

    @Test
    @DisplayName("Should not expose access denial exception details")
    void shouldNotExposeAccessDeniedExceptionDetails()
        throws Exception {

      String sensitiveDetails =
          "Sensitive authorization implementation details";

      handleAccessDenied(
          new AccessDeniedException(sensitiveDetails)
      );

      String body = response.getContentAsString(
          StandardCharsets.UTF_8
      );

      assertAll(
          () -> assertFalse(
              body.contains(sensitiveDetails)
          ),
          () -> assertFalse(
              body.contains(
                  AccessDeniedException.class.getSimpleName()
              )
          ),
          () -> assertEquals(
              ApiErrorMessages.ACCESS_DENIED_RESOURCE,
              readErrorResponse().message()
          )
      );
    }

    @Test
    @DisplayName("Should preserve the response character encoding as UTF-8")
    void shouldPreserveResponseCharacterEncodingAsUtf8()
        throws Exception {

      handleAccessDenied();

      String body = response.getContentAsString(
          StandardCharsets.UTF_8
      );

      assertAll(
          () -> assertEquals(
              StandardCharsets.UTF_8.name(),
              response.getCharacterEncoding()
          ),
          () -> assertTrue(
              body.contains(
                  ApiErrorMessages.ACCESS_DENIED_RESOURCE
              )
          ),
          () -> assertFalse(
              body.contains("VocÃª")
          )
      );
    }
  }

  private void handleAccessDenied() throws Exception {
    handleAccessDenied(
        new AccessDeniedException("Access denied")
    );
  }

  private void handleAccessDenied(
      AccessDeniedException exception
  ) throws Exception {

    accessDeniedHandler.handle(
        request,
        response,
        exception
    );
  }

  private void assertForbiddenResponse() throws Exception {
    ApiErrorResponse errorResponse = readErrorResponse();

    MediaType contentType = MediaType.parseMediaType(
        response.getContentType()
    );

    assertAll(
        () -> assertEquals(
            FORBIDDEN_STATUS,
            response.getStatus()
        ),
        () -> assertTrue(
            MediaType.APPLICATION_JSON.isCompatibleWith(
                contentType
            )
        ),
        () -> assertEquals(
            StandardCharsets.UTF_8.name(),
            response.getCharacterEncoding()
        ),
        () -> assertEquals(
            FORBIDDEN_STATUS,
            errorResponse.status()
        ),
        () -> assertEquals(
            ApiErrorMessages.ACCESS_DENIED_RESOURCE,
            errorResponse.message()
        ),
        () -> assertEquals(
            FIXED_INSTANT,
            errorResponse.timestamp()
        )
    );
  }

  private ApiErrorResponse readErrorResponse() throws Exception {
    return jsonMapper.readValue(
        response.getContentAsString(StandardCharsets.UTF_8),
        ApiErrorResponse.class
    );
  }
}
