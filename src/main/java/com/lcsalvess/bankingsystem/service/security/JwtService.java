package com.lcsalvess.bankingsystem.service.security;

import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    /**
     * Custom JWT claim that stores the user's credential version.
     * Uses "tv" as an abbreviation for "token version" to keep the token compact.
     */
    static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey signingKey;
    private final JwtParser jwtParser;
    private final long expirationInSeconds;
    private final String issuer;
    private final Clock clock;

    /**
     * Creates the service after validating the signing key.
     */
    public static JwtService create(JwtProperties jwtProperties, Clock clock) {
        SecretKey signingKey = createSigningKey(jwtProperties.secret());

        return new JwtService(
                signingKey,
                jwtProperties.expiration(),
                jwtProperties.issuer(),
                clock
        );
    }

    /**
     * Initializes the configuration and builds the JWT parser only once.
     */
    private JwtService(
            SecretKey signingKey,
            long expirationInSeconds,
            String issuer,
            Clock clock
    ) {
        this.signingKey = signingKey;
        this.expirationInSeconds = expirationInSeconds;
        this.issuer = issuer;
        this.clock = clock;

        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    private static SecretKey createSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    ApiErrorMessages.JWT_SECRET_REQUIRED
            );
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (DecodingException | WeakKeyException exception) {
            throw new IllegalStateException(
                    ApiErrorMessages.JWT_SECRET_INVALID_CONFIGURATION,
                    exception
            );
        }
    }

    public String generateToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiration = issuedAt.plusSeconds(expirationInSeconds);

        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String jwt) {
        return parse(jwt).username();
    }

    /**
     * Validates the signature, issuer, expiration, and required token-version claim.
     * The "tv" claim must contain a non-negative integer within the supported range.
     */
    public JwtClaims parse(String jwt) {
        Claims claims = jwtParser
                .parseSignedClaims(jwt)
                .getPayload();

        int tokenVersion = extractTokenVersion(claims);

        return new JwtClaims(claims.getSubject(), tokenVersion);
    }

    private int extractTokenVersion(Claims claims) {
        Object version = claims.get(TOKEN_VERSION_CLAIM);

        if (!(version instanceof Number number)) {
            throw new JwtException(
                    ApiErrorMessages.JWT_TOKEN_VERSION_INVALID
            );
        }

        try {
            int tokenVersion = new BigDecimal(number.toString()).intValueExact();

            if (tokenVersion < 0) {
                throw new JwtException(
                        ApiErrorMessages.JWT_TOKEN_VERSION_INVALID
                );
            }

            return tokenVersion;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new JwtException(
                    ApiErrorMessages.JWT_TOKEN_VERSION_INVALID,
                    exception
            );
        }
    }
}
