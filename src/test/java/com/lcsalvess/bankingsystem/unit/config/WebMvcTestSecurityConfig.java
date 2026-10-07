package com.lcsalvess.bankingsystem.unit.config;

import com.lcsalvess.bankingsystem.security.RestAuthenticationEntryPoint;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class WebMvcTestSecurityConfig {

    @Bean
    JwtService jwtService() {
        return Mockito.mock(JwtService.class);
    }

    @Bean
    CustomUserDetailsService customUserDetailsService() {
        return Mockito.mock(CustomUserDetailsService.class);
    }

    @Bean
    RestAuthenticationEntryPoint authenticationEntryPoint() {
        return Mockito.mock(RestAuthenticationEntryPoint.class);
    }
}