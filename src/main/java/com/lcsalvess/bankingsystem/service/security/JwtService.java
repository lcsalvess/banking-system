package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationInSeconds;
    private final String issuer;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationInSeconds,
            @Value("${jwt.issuer}") String issuer
    ) {
        this.signingKey = createSigningKey(secret);
        this.expirationInSeconds = expirationInSeconds;
        this.issuer = issuer;
    }

    private SecretKey createSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("O segredo JWT não pode ser nulo ou vazio.");
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException | WeakKeyException exception) {
            throw new IllegalStateException(
                    "Configuração do segredo JWT inválida.",
                    exception);
        }
    }

    public String generateToken(User user) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationInSeconds * 1000);

        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String jwt) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject();
    }
}
