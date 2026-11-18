package com.lcsalvess.bankingsystem.config.security;

import com.lcsalvess.bankingsystem.service.security.JwtService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the JWT service bean used for token generation and validation.
 */
@Configuration(proxyBeanMethods = false)
public class JwtServiceConfiguration {

  /**
   * Creates the JWT service using the application security configuration.
   *
   * @param jwtProperties the JWT configuration properties
   * @param clock the clock used for token timestamps and expiration validation
   * @return the configured JWT service
   */
  @Bean
  public JwtService jwtService(
      JwtProperties jwtProperties,
      Clock clock
  ) {
    return JwtService.create(jwtProperties, clock);
  }
}
