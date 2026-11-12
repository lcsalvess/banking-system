package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.config.security.JwtProperties;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.service.security.JwtClaims;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import com.lcsalvess.bankingsystem.testsupport.UserTestFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    // Base64 encoding of "uma-chave-secreta-com-pelo-menos-32-bytes" (41 bytes → HS256)
    private static final String SECRET =
            "dW1hLWNoYXZlLXNlY3JldGEtY29tLXBlbG8tbWVub3MtMzItYnl0ZXM=";

    // A different key, used to simulate tokens issued by third parties
    private static final String OTHER_SECRET =
            "b3V0cmEtY2hhdmUtc2VjcmV0YS1jb21wbGV0YW1lbnRlLWRpZmVyZW50ZS02NA==";

    private static final String ISSUER = "banking-system-test";
    private static final long EXPIRATION_IN_SECONDS = 3600L;

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);
        user = UserTestFactory.aUser().build();
    }

    @Nested
    @DisplayName("On service construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should create the service with valid configuration")
        void shouldCreateServiceWithValidConfiguration() {
            assertDoesNotThrow(() ->
                    serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER));
        }

        @Test
        @DisplayName("Should accept a key with exactly 256 bits")
        void shouldAcceptKeyWithMinimumSize() {
            String secret = base64OfBytes(32);

            assertDoesNotThrow(() ->
                    serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER));
        }

        @ParameterizedTest(name = "secret = \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        @DisplayName("Should reject null, empty, or blank secrets")
        void shouldRejectNullOrBlankSecret(String secret) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(ApiErrorMessages.JWT_SECRET_REQUIRED, exception.getMessage());
        }

        @ParameterizedTest(name = "secret = \"{0}\"")
        @ValueSource(strings = {"###", "abc", "not valid base64 !!!"})
        @DisplayName("Should reject secrets that are not valid Base64")
        void shouldRejectSecretThatIsNotValidBase64(String secret) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(ApiErrorMessages.JWT_SECRET_INVALID_CONFIGURATION, exception.getMessage());
            assertTrue(
                    exception.getCause() instanceof DecodingException
                            || exception.getCause() instanceof WeakKeyException,
                    "The cause should be DecodingException or WeakKeyException"
            );
        }

        @ParameterizedTest(name = "key with {0} byte(s)")
        @ValueSource(ints = {1, 16, 31})
        @DisplayName("Should reject keys shorter than 256 bits")
        void shouldRejectWeakKey(int sizeInBytes) {
            String secret = base64OfBytes(sizeInBytes);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(ApiErrorMessages.JWT_SECRET_INVALID_CONFIGURATION, exception.getMessage());
            assertInstanceOf(WeakKeyException.class, exception.getCause());
        }

        @Test
        @DisplayName("Should not expose the secret in the error message")
        void shouldNotLeakSecretInErrorMessage() {
            String weakSecret = base64OfBytes(16);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(weakSecret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertFalse(exception.getMessage().contains(weakSecret));
        }
    }

    @Nested
    @DisplayName("On JWT generation")
    class GenerateTokenTests {

        @Test
        @DisplayName("Should generate a compact JWT with three parts")
        void shouldGenerateCompactJwtWithThreeParts() {
            String token = jwtService.generateToken(user);

            assertNotNull(token);
            assertFalse(token.isBlank());
            assertEquals(3, token.split("\\.", -1).length);
        }

        @Test
        @DisplayName("Should use the username as the subject and the configured issuer")
        void shouldSetSubjectAndIssuer() {
            Claims claims = parse(jwtService.generateToken(user)).getPayload();

            assertEquals(user.getUsername(), claims.getSubject());
            assertEquals(ISSUER, claims.getIssuer());
        }

        @Test
        @DisplayName("Should set iat and exp using the injected Clock")
        void shouldSetIssuedAtAndExpirationFromClock() {
            Instant now = Instant.parse("2026-01-15T02:30:00Z");
            JwtService service = serviceOf(SECRET, 3600, ISSUER, fixedClock(now));

            Claims claims = parseAt(service.generateToken(user), now).getPayload();

            assertEquals(now, claims.getIssuedAt().toInstant());
            assertEquals(now.plusSeconds(3600), claims.getExpiration().toInstant());
        }

        @ParameterizedTest(name = "expiration = {0}s")
        @ValueSource(longs = {60, 3600, 86400})
        @DisplayName("Should respect the configured expiration")
        void shouldUseConfiguredExpiration(long expirationInSeconds) {
            JwtService service = serviceOf(SECRET, expirationInSeconds, ISSUER);

            Claims claims = parse(service.generateToken(user)).getPayload();

            assertEquals(
                    Duration.ofSeconds(expirationInSeconds),
                    Duration.between(
                            claims.getIssuedAt().toInstant(),
                            claims.getExpiration().toInstant()
                    )
            );
        }

        @Test
        @DisplayName("Should sign the token using HMAC SHA-256")
        void shouldSignTokenWithHs256() {
            Jws<Claims> jws = parse(jwtService.generateToken(user));

            assertEquals("HS256", jws.getHeader().getAlgorithm());
        }

        @Test
        @DisplayName("Should include only standard claims and the token version")
        void shouldContainOnlyStandardClaims() {
            Claims claims = parse(jwtService.generateToken(user)).getPayload();

            assertEquals(Set.of("sub", "iss", "iat", "exp", "tv"), claims.keySet());
        }

        @Test
        @DisplayName("Should include the user's credential version in the tv claim")
        void shouldIncludeTokenVersion() {
            user.changePassword("new-hash");

            Claims claims = parse(jwtService.generateToken(user)).getPayload();

            assertEquals(1, ((Number) claims.get("tv")).intValue());
        }

        @Test
        @DisplayName("Should generate different tokens for different users")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            User firstUser = UserTestFactory.aUser()
                    .withUsername("first.user")
                    .withEmail("first.user@test.com")
                    .build();

            User secondUser = UserTestFactory.aUser()
                    .withUsername("second.user")
                    .withEmail("second.user@test.com")
                    .build();

            String first = jwtService.generateToken(firstUser);
            String second = jwtService.generateToken(secondUser);

            assertNotEquals(first, second);
        }
    }

    @Nested
    @DisplayName("When extracting the username from a JWT")
    class ExtractUsernameTests {

        @Test
        @DisplayName("Should extract the username from a valid token")
        void shouldExtractUsernameFromValidToken() {
            String token = jwtService.generateToken(user);

            assertEquals(user.getUsername(), jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should accept a token issued by another instance with the same configuration")
        void shouldAcceptTokenIssuedByAnotherInstanceWithSameConfiguration() {
            JwtService issuer = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);
            JwtService validator = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);

            String token = issuer.generateToken(user);

            assertEquals(user.getUsername(), validator.extractUsername(token));
        }

        @Test
        @DisplayName("Should validate expiration using the injected Clock")
        void shouldValidateExpirationAgainstInjectedClock() {
            Instant issuedAt = Instant.parse("2026-01-15T02:30:00Z");
            String token = serviceOf(SECRET, 3600, ISSUER, fixedClock(issuedAt)).generateToken(user);

            JwtService beforeExpiration = serviceOf(SECRET, 3600, ISSUER, fixedClock(issuedAt.plusSeconds(3599)));
            JwtService afterExpiration = serviceOf(SECRET, 3600, ISSUER, fixedClock(issuedAt.plusSeconds(3601)));

            assertEquals(user.getUsername(), beforeExpiration.extractUsername(token));
            assertThrows(ExpiredJwtException.class, () -> afterExpiration.extractUsername(token));
        }

        @Test
        @DisplayName("Should reject an expired token")
        void shouldRejectExpiredToken() {
            JwtService service = serviceOf(SECRET, -60, ISSUER);
            String token = service.generateToken(user);

            assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should reject a token with a tampered payload")
        void shouldRejectTokenWithTamperedPayload() {
            String[] parts = jwtService.generateToken(user).split("\\.", -1);
            long exp = Instant.now().plusSeconds(3600).getEpochSecond();
            String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"admin\",\"iss\":\"" + ISSUER + "\",\"exp\":" + exp + "}")
                            .getBytes(StandardCharsets.UTF_8)
            );
            String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

            assertThrows(SignatureException.class, () -> jwtService.extractUsername(forgedToken));
        }

        @Test
        @DisplayName("Should reject a token signed with a different key")
        void shouldRejectTokenSignedWithAnotherKey() {
            JwtService foreign = serviceOf(OTHER_SECRET, EXPIRATION_IN_SECONDS, ISSUER);
            String token = foreign.generateToken(user);

            assertThrows(SignatureException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should reject a token with a different issuer")
        void shouldRejectTokenWithDifferentIssuer() {
            JwtService foreign = serviceOf(SECRET, EXPIRATION_IN_SECONDS, "another-issuer");
            String token = foreign.generateToken(user);

            assertThrows(IncorrectClaimException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should reject a token without an issuer")
        void shouldRejectTokenWithoutIssuer() {
            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .expiration(inOneHour())
                    .signWith(key(SECRET))
                    .compact();

            assertThrows(MissingClaimException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Should reject an unsigned token")
        void shouldRejectUnsignedToken() {
            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .issuer(ISSUER)
                    .expiration(inOneHour())
                    .compact();

            assertThrows(UnsupportedJwtException.class, () -> jwtService.extractUsername(token));
        }

        @ParameterizedTest(name = "token = \"{0}\"")
        @ValueSource(strings = {"not-a-jwt", "a.b.c", "a.b", "..", "Bearer token"})
        @DisplayName("Should reject malformed tokens")
        void shouldRejectMalformedToken(String token) {
            assertThrows(JwtException.class, () -> jwtService.extractUsername(token));
        }

        @ParameterizedTest(name = "token = \"{0}\"")
        @NullAndEmptySource
        @DisplayName("Should reject null or empty tokens")
        void shouldRejectNullOrEmptyToken(String token) {
            assertThrows(IllegalArgumentException.class, () -> jwtService.extractUsername(token));
        }
    }

    @Nested
    @DisplayName("When reading claims from a JWT")
    class ParseTests {

        @Test
        @DisplayName("Should return the username and token version")
        void shouldReturnUsernameAndTokenVersion() {
            user.changePassword("new-hash");
            user.changePassword("another-hash");

            JwtClaims claims = jwtService.parse(jwtService.generateToken(user));

            assertEquals(user.getUsername(), claims.username());
            assertEquals(2, claims.tokenVersion());
        }

        @Test
        @DisplayName("Should reject a token without the version claim")
        void shouldRejectTokenWithoutVersionClaim() {
            String tokenWithoutVersion = Jwts.builder()
                    .subject(user.getUsername())
                    .issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(inOneHour())
                    .signWith(key(SECRET))
                    .compact();

            assertThrows(
                    JwtException.class,
                    () -> jwtService.parse(tokenWithoutVersion)
            );
        }

        @Test
        @DisplayName("Should reject an expired token")
        void shouldRejectExpiredToken() {
            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                    .expiration(Date.from(Instant.now().minusSeconds(60)))
                    .claim("tv", 0)
                    .signWith(key(SECRET))
                    .compact();

            assertThrows(ExpiredJwtException.class, () -> jwtService.parse(token));
        }

        @Test
        @DisplayName("Should reject a token signed with a different key")
        void shouldRejectTokenSignedWithAnotherKey() {
            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(inOneHour())
                    .claim("tv", 0)
                    .signWith(key(OTHER_SECRET))
                    .compact();

            assertThrows(SignatureException.class, () -> jwtService.parse(token));
        }

        @Test
        @DisplayName("Should reject a token with an invalid tv claim")
        void shouldRejectTokenWithInvalidVersionClaim() {
            String token = Jwts.builder()
                    .subject(user.getUsername())
                    .issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(inOneHour())
                    .claim("tv", "invalid")
                    .signWith(key(SECRET))
                    .compact();

            assertThrows(
                    JwtException.class,
                    () -> jwtService.parse(token)
            );
        }
    }

    private static JwtService serviceOf(String secret, long expirationInSeconds, String issuer) {
        return serviceOf(secret, expirationInSeconds, issuer, Clock.systemUTC());
    }

    private static JwtService serviceOf(
            String secret,
            long expirationInSeconds,
            String issuer,
            Clock clock
    ) {
        return JwtService.create(
                new JwtProperties(secret, expirationInSeconds, issuer),
                clock
        );
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private static Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key(SECRET))
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token);
    }

    private static Jws<Claims> parseAt(String token, Instant now) {
        return Jwts.parser()
                .verifyWith(key(SECRET))
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(now))
                .build()
                .parseSignedClaims(token);
    }

    private static Date inOneHour() {
        return Date.from(Instant.now().plusSeconds(3600));
    }

    private static String base64OfBytes(int sizeInBytes) {
        byte[] bytes = new byte[sizeInBytes];
        Arrays.fill(bytes, (byte) 1);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
