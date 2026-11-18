package com.lcsalvess.bankingsystem.service.security;

/**
 * Claims from a JWT that has already been validated (signature, issuer, and expiration).
 *
 * @param username     the token's subject
 * @param tokenVersion the user's credential version when the token was issued
 */
public record JwtClaims(
    String username,
    int tokenVersion
) {
}
