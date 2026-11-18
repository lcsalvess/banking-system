package com.lcsalvess.bankingsystem.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds the configuration properties required for JWT generation and validation.
 *
 * @param secret the Base64-encoded secret used to sign and verify JWTs
 * @param expiration the token validity period in seconds
 * @param issuer the expected JWT issuer
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long expiration,
    String issuer
) {
}
