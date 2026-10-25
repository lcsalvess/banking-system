package com.lcsalvess.bankingsystem.config.security;

import com.lcsalvess.bankingsystem.service.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class JwtServiceConfiguration {

    @Bean
    public JwtService jwtService(
            JwtProperties jwtProperties,
            Clock clock
    ) {
        return JwtService.create(jwtProperties, clock);
    }
}