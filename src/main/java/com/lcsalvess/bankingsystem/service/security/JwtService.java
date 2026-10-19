package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;

@Service
public class JwtService {

    /**
     * Claim personalizada do JWT que armazena a versão das credenciais do usuário.
     * Utiliza "tv" como abreviação de "token version" para manter o token compacto.
     */
    static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey signingKey;
    private final long expirationInSeconds;
    private final String issuer;
    private final Clock clock;

    public JwtService(JwtProperties jwtProperties, Clock clock) {
        this.signingKey = createSigningKey(jwtProperties.secret());
        this.expirationInSeconds = jwtProperties.expiration();
        this.issuer = jwtProperties.issuer();
        this.clock = clock;
    }

    private SecretKey createSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(ApiErrorMessages.JWT_SECRET_REQUIRED);
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException | WeakKeyException exception) {
            throw new IllegalStateException(
                    ApiErrorMessages.JWT_SECRET_INVALID_CONFIGURATION,
                    exception);
        }
    }

    public String generateToken(User user) {
        Date issuedAt = Date.from(clock.instant());
        Date expiration = new Date(issuedAt.getTime() + expirationInSeconds * 1000);

        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String jwt) {
        return parse(jwt).username();
    }

    /**
     * Valida assinatura, issuer e expiração e devolve os claims usados na autenticação.
     * Tokens emitidos antes da introdução do claim de versão valem como versão 0.
     */
    public JwtClaims parse(String jwt) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        int tokenVersion = extractTokenVersion(claims);

        return new JwtClaims(claims.getSubject(), tokenVersion);
    }

    private int extractTokenVersion(Claims claims) {
        Object version = claims.get(TOKEN_VERSION_CLAIM);

        if (version == null) {
            // Compatibilidade com tokens emitidos antes da versão de credenciais.
            return 0;
        }

        if (!(version instanceof Number number)
                || number.longValue() < 0
                || number.longValue() > Integer.MAX_VALUE
                || number.doubleValue() != number.intValue()) {
            throw new JwtException(
                    ApiErrorMessages.JWT_TOKEN_VERSION_INVALID);
        }

        return number.intValue();
    }
}
