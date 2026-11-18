package com.lcsalvess.bankingsystem.unit.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lcsalvess.bankingsystem.audit.web.AuditRequestAttributes;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.security.filter.JwtAuthenticationFilter;
import com.lcsalvess.bankingsystem.security.handler.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtClaims;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTests {

  private static final String TOKEN = "valid.jwt.token";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_TOKEN = "Bearer " + TOKEN;

  @Mock
  private JwtService jwtService;

  @Mock
  private CustomUserDetailsService userDetailsService;

  @Mock
  private RestAuthenticationEntryPoint authenticationEntryPoint;

  @Mock
  private FilterChain filterChain;

  private JwtAuthenticationFilter filter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();

    filter = new JwtAuthenticationFilter(
        jwtService,
        userDetailsService,
        authenticationEntryPoint
    );

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Should continue the filter chain when the authorization header is missing")
  void shouldContinueFilterChainWhenAuthorizationHeaderIsMissing()
      throws Exception {

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);

    verifyNoInteractions(
        jwtService,
        userDetailsService,
        authenticationEntryPoint
    );

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should continue the filter chain when the authorization scheme is not Bearer")
  void shouldContinueFilterChainWhenAuthorizationSchemeIsNotBearer()
      throws Exception {

    request.addHeader(AUTHORIZATION_HEADER, "Basic dXNlcjpwYXNz");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);

    verifyNoInteractions(
        jwtService,
        userDetailsService,
        authenticationEntryPoint
    );

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should continue the filter chain when the Bearer token is empty")
  void shouldContinueFilterChainWhenBearerTokenIsEmpty()
      throws Exception {

    request.addHeader(AUTHORIZATION_HEADER, "Bearer    ");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);

    verifyNoInteractions(
        jwtService,
        userDetailsService,
        authenticationEntryPoint
    );

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should skip JWT authentication for the login endpoint")
  void shouldSkipFilterForLoginEndpoint() throws Exception {
    request = new MockHttpServletRequest(
        "POST",
        "/api/v1/auth/login"
    );
    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);

    verifyNoInteractions(
        jwtService,
        userDetailsService,
        authenticationEntryPoint
    );

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should not skip JWT authentication for GET requests to the login endpoint")
  void shouldNotSkipFilterForGetRequestToLoginEndpoint()
      throws Exception {

    request = new MockHttpServletRequest(
        "GET",
        "/api/v1/auth/login"
    );
    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    doThrow(new MalformedJwtException("Invalid token"))
        .when(jwtService)
        .parse(TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(jwtService).parse(TOKEN);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(BadCredentialsException.class)
    );

    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  @DisplayName("Should authenticate the user when the JWT and token version are valid")
  void shouldAuthenticateUserWhenTokenIsValid() throws Exception {
    User user = UserTestFactory.userBuilder().build();

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims(user.getUsername(), user.getTokenVersion()));

    when(userDetailsService.loadUserByUsername(user.getUsername()))
        .thenReturn(user);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(jwtService).parse(TOKEN);

    verify(userDetailsService).loadUserByUsername(user.getUsername());

    verify(filterChain).doFilter(request, response);

    Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();

    assertSame(user, authentication.getPrincipal());
    assertTrue(authentication.isAuthenticated());
    assertEquals(
        user.getAuthorities(),
        authentication.getAuthorities()
    );

    assertSame(
        user,
        request.getAttribute(
            AuditRequestAttributes.AUTHENTICATED_USER
        )
    );

    verifyNoInteractions(authenticationEntryPoint);
  }

  @Test
  @DisplayName("Should reject authentication when the user is disabled")
  void shouldRejectAuthenticationWhenUserIsDisabled()
      throws Exception {

    User user = UserTestFactory.userBuilder()
        .inactive()
        .build();

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims(user.getUsername(), user.getTokenVersion()));

    when(userDetailsService.loadUserByUsername(user.getUsername()))
        .thenReturn(user);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(DisabledException.class)
    );

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());

    assertNull(
        request.getAttribute(
            AuditRequestAttributes.AUTHENTICATED_USER
        )
    );
  }

  @Test
  @DisplayName("Should reject authentication when UserDetails is not a User entity")
  void shouldRejectAuthenticationWhenUserDetailsIsNotUserEntity()
      throws Exception {

    UserDetails userDetails = org.mockito.Mockito.mock(UserDetails.class);

    when(userDetails.isEnabled()).thenReturn(true);

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims("test-user", 0));

    when(userDetailsService.loadUserByUsername("test-user"))
        .thenReturn(userDetails);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(BadCredentialsException.class)
    );

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());

    assertNull(
        request.getAttribute(
            AuditRequestAttributes.AUTHENTICATED_USER
        )
    );
  }

  @Test
  @DisplayName("Should reject authentication when the token version does not match")
  void shouldRejectAuthenticationWhenTokenVersionDoesNotMatch()
      throws Exception {

    User user = UserTestFactory.userBuilder().build();

    user.changePassword("new-encoded-password");

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims(user.getUsername(), 0));

    when(userDetailsService.loadUserByUsername(user.getUsername()))
        .thenReturn(user);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(BadCredentialsException.class)
    );

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());

    assertNull(
        request.getAttribute(
            AuditRequestAttributes.AUTHENTICATED_USER
        )
    );
  }

  @Test
  @DisplayName("Should reject authentication when JWT parsing fails")
  void shouldRejectAuthenticationWhenJwtParsingFails()
      throws Exception {

    doThrow(new MalformedJwtException("Malformed JWT"))
        .when(jwtService)
        .parse(TOKEN);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(BadCredentialsException.class)
    );

    verifyNoInteractions(userDetailsService);

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should reject authentication when the user does not exist")
  void shouldRejectAuthenticationWhenUserDoesNotExist()
      throws Exception {

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims("non-existent-user", 0));

    when(userDetailsService.loadUserByUsername("non-existent-user"))
        .thenThrow(new UsernameNotFoundException("User not found"));

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commence(
        eq(request),
        eq(response),
        any(BadCredentialsException.class)
    );

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should return service unavailable when database access fails")
  void shouldReturnServiceUnavailableWhenDatabaseAccessFails()
      throws Exception {

    User user = UserTestFactory.userBuilder().build();

    when(jwtService.parse(TOKEN))
        .thenReturn(new JwtClaims(user.getUsername(), user.getTokenVersion()));

    when(userDetailsService.loadUserByUsername(user.getUsername()))
        .thenThrow(
            new DataAccessResourceFailureException(
                "Database unavailable"
            )
        );

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commenceServiceUnavailable(response);

    verify(authenticationEntryPoint, never()).commence(
        eq(request),
        eq(response),
        any()
    );

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Should return service unavailable when a transaction fails")
  void shouldReturnServiceUnavailableWhenTransactionFails()
      throws Exception {

    doThrow(new TransactionSystemException("Transaction failed"))
        .when(jwtService)
        .parse(TOKEN);

    request.addHeader(AUTHORIZATION_HEADER, BEARER_TOKEN);

    filter.doFilter(request, response, filterChain);

    verify(authenticationEntryPoint).commenceServiceUnavailable(response);

    verify(authenticationEntryPoint, never()).commence(
        eq(request),
        eq(response),
        any()
    );

    verifyNoInteractions(userDetailsService);

    verify(filterChain, never()).doFilter(request, response);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
