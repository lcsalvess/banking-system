package com.lcsalvess.bankingsystem.config;

import com.lcsalvess.bankingsystem.filter.CorrelationIdFilter;
import com.lcsalvess.bankingsystem.security.JwtAuthenticationFilter;
import com.lcsalvess.bankingsystem.security.RestAccessDeniedHandler;
import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorrelationIdFilter correlationIdFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public access: login only.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Password change: any authenticated user.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/auth/password"
                        ).authenticated()

                        // OpenAPI documentation.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Actuator: health and info.
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .hasAnyRole("EMPLOYEE", "ADMIN")

                        // Actuator: metrics restricted to administrators.
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                        .hasRole("ADMIN")

                        // User management: administrators only.
                        .requestMatchers("/api/v1/users", "/api/v1/users/**")
                        .hasRole("ADMIN")

                        // Client management: employees only.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/clients"
                        ).hasRole("EMPLOYEE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/clients",
                                "/api/v1/clients/**"
                        ).hasRole("EMPLOYEE")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/clients/**"
                        ).hasRole("EMPLOYEE")

                        // Account management: employees only.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/accounts"
                        ).hasRole("EMPLOYEE")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/accounts",
                                "/api/v1/accounts/**"
                        ).hasRole("EMPLOYEE")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/accounts/**"
                        ).hasRole("EMPLOYEE")

                        // Financial operations: employees only.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/transactions/deposit",
                                "/api/v1/transactions/withdraw",
                                "/api/v1/transactions/transfer"
                        ).hasRole("EMPLOYEE")

                        // Transaction history and lookup: read-only access.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/transactions/**"
                        ).hasAnyRole("EMPLOYEE", "ADMIN")

                        // All remaining endpoints require authentication.
                        .anyRequest().authenticated()
                )
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