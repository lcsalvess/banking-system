package com.lcsalvess.bankingsystem.config.security;

import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.JwtAuthenticationFilter;
import com.lcsalvess.bankingsystem.security.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
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

@EnableMethodSecurity
@Configuration
public class SecurityConfig {

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
                            "/api/v1/transactions/deposit",
                            "/api/v1/transactions/withdraw",
                            "/api/v1/transactions/transfer"
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig
    ) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistrationBean(
            CorrelationIdFilter filter
    ) {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    jwtAuthenticationFilterRegistrationBean(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}