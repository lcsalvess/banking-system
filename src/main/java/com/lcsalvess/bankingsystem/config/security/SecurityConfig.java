package com.lcsalvess.bankingsystem.config.security;

import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.filter.JwtAuthenticationFilter;
import com.lcsalvess.bankingsystem.security.handler.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.handler.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configures application security, authentication, and servlet filter registration.
 *
 * <p>Defines stateless JWT-based authentication, endpoint authorization rules,
 * security exception handlers, and password encoding.
 */
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

  /**
   * Configures the application's HTTP security filter chain.
   *
   * <p>Defines endpoint authorization rules, exception handling, stateless
   * session management, and JWT and correlation ID filter ordering.
   *
   * @param http                     the HTTP security configuration
   * @param jwtAuthenticationFilter  the filter responsible for JWT authentication
   * @param correlationIdFilter      the filter responsible for request correlation IDs
   * @param authenticationEntryPoint the handler for unauthenticated requests
   * @param accessDeniedHandler      the handler for unauthorized access attempts
   * @param environment              the application environment used to determine active profiles
   * @return the configured security filter chain
   * @throws Exception if the security filter chain cannot be configured
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CorrelationIdFilter correlationIdFilter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      Environment environment
  ) throws Exception {

    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> {
          // Public access: login only.
          auth.requestMatchers(
              HttpMethod.POST,
              "/api/v1/auth/login"
          ).permitAll();

          // Password change: any authenticated user.
          auth.requestMatchers(
              HttpMethod.PATCH,
              "/api/v1/auth/password"
          ).authenticated();

          // OpenAPI documentation: local profile only, never in production.
          if (environment.acceptsProfiles(Profiles.of("local & !prod"))) {
            auth.requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**"
            ).permitAll();
          }

          // Actuator: public liveness and readiness probes.
          auth.requestMatchers(
              "/actuator/health/liveness",
              "/actuator/health/readiness"
          ).permitAll();

          // Actuator: health and info.
          auth.requestMatchers(
              "/actuator/health",
              "/actuator/info"
          ).hasAnyRole("EMPLOYEE", "ADMIN");

          // Actuator: metrics restricted to administrators.
          auth.requestMatchers(
              "/actuator/metrics",
              "/actuator/metrics/**"
          ).hasRole("ADMIN");

          // Current user profile: any authenticated user.
          auth.requestMatchers(
              HttpMethod.GET,
              "/api/v1/users/me"
          ).authenticated();

          // User management: administrators only.
          auth.requestMatchers(
              "/api/v1/users",
              "/api/v1/users/**"
          ).hasRole("ADMIN");

          // Client management: employees only.
          auth.requestMatchers(
              HttpMethod.POST,
              "/api/v1/clients"
          ).hasRole("EMPLOYEE");

          auth.requestMatchers(
              HttpMethod.GET,
              "/api/v1/clients",
              "/api/v1/clients/**"
          ).hasRole("EMPLOYEE");

          auth.requestMatchers(
              HttpMethod.PATCH,
              "/api/v1/clients/**"
          ).hasRole("EMPLOYEE");

          // Account management: employees only.
          auth.requestMatchers(
              HttpMethod.POST,
              "/api/v1/accounts"
          ).hasRole("EMPLOYEE");

          auth.requestMatchers(
              HttpMethod.GET,
              "/api/v1/accounts",
              "/api/v1/accounts/**"
          ).hasRole("EMPLOYEE");

          auth.requestMatchers(
              HttpMethod.PATCH,
              "/api/v1/accounts/**"
          ).hasRole("EMPLOYEE");

          // Financial operations: employees only.
          auth.requestMatchers(
              HttpMethod.POST,
              "/api/v1/transactions/deposits",
              "/api/v1/transactions/withdrawals",
              "/api/v1/transactions/transfers"
          ).hasRole("EMPLOYEE");

          // Transaction history and lookup: employees only.
          auth.requestMatchers(
              HttpMethod.GET,
              "/api/v1/transactions/**"
          ).hasRole("EMPLOYEE");

          // Audit trail: read-only access for auditors.
          auth.requestMatchers(
              HttpMethod.GET,
              "/api/v1/audit-events",
              "/api/v1/audit-events/**"
          ).hasRole("AUDITOR");

          // Deny any endpoint not explicitly authorized above.
          auth.anyRequest().denyAll();
        })

        .exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class
        )
        .addFilterBefore(
            correlationIdFilter,
            JwtAuthenticationFilter.class
        )
        .build();
  }

  /**
   * Creates the password encoder used for secure password hashing.
   *
   * <p>Uses BCrypt with a strength factor of 12.
   *
   * @return the configured BCrypt password encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Exposes the application's authentication manager.
   *
   * @param authConfig the Spring Security authentication configuration
   * @return the configured authentication manager
   * @throws Exception if the authentication manager cannot be retrieved
   */
  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authConfig
  ) throws Exception {
    return authConfig.getAuthenticationManager();
  }

  /**
   * Prevents automatic servlet-container registration of the correlation ID filter.
   *
   * <p>The filter is registered explicitly within the Spring Security filter chain.
   *
   * @param filter the correlation ID filter to configure
   * @return the disabled servlet filter registration
   */
  @Bean
  public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistrationBean(
      CorrelationIdFilter filter
  ) {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(filter);

    registration.setEnabled(false);

    return registration;
  }

  /**
   * Prevents automatic servlet-container registration of the JWT authentication filter.
   *
   * <p>The filter is registered explicitly within the Spring Security filter chain.
   *
   * @param filter the JWT authentication filter to configure
   * @return the disabled servlet filter registration
   */
  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistrationBean(
      JwtAuthenticationFilter filter
  ) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);

    registration.setEnabled(false);

    return registration;
  }
}
