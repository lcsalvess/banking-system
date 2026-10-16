package com.lcsalvess.bankingsystem.service.security;

/**
 * Claims de um JWT já validado (assinatura, issuer e expiração).
 *
 * @param username     subject do token
 * @param tokenVersion versão das credenciais do usuário na emissão do token
 */
public record JwtClaims(
        String username,
        int tokenVersion
) {
}
