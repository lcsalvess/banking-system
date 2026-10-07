package com.lcsalvess.bankingsystem.unit.service.security;

import com.lcsalvess.bankingsystem.config.JwtProperties;
import com.lcsalvess.bankingsystem.entity.User;
import com.lcsalvess.bankingsystem.entity.enums.Role;
import com.lcsalvess.bankingsystem.service.security.JwtService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {

    // Base64 de "uma-chave-secreta-com-pelo-menos-32-bytes" (41 bytes -> HS256)
    private static final String SECRET =
            "dW1hLWNoYXZlLXNlY3JldGEtY29tLXBlbG8tbWVub3MtMzItYnl0ZXM=";

    // Chave diferente, usada para simular tokens emitidos por terceiros
    private static final String OTHER_SECRET =
            "b3V0cmEtY2hhdmUtc2VjcmV0YS1jb21wbGV0YW1lbnRlLWRpZmVyZW50ZS02NA==";

    private static final String ISSUER = "banking-system-test";
    private static final long EXPIRATION_IN_SECONDS = 3600L;
    private static final String USERNAME = "usuario.teste";

    private static final String BLANK_SECRET_MESSAGE =
            "O segredo JWT não pode ser nulo ou vazio.";
    private static final String INVALID_SECRET_MESSAGE =
            "Configuração do segredo JWT inválida.";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);
        user = createUser(USERNAME);
    }

    @Nested
    @DisplayName("Ao criar o serviço")
    class ConstructionTests {

        @Test
        @DisplayName("Deve criar o serviço com configuração válida")
        void shouldCreateServiceWithValidConfiguration() {
            assertDoesNotThrow(() ->
                    serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER));
        }

        @Test
        @DisplayName("Deve aceitar chave com exatamente 256 bits")
        void shouldAcceptKeyWithMinimumSize() {
            String secret = base64OfBytes(32);

            assertDoesNotThrow(() ->
                    serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER));
        }

        @ParameterizedTest(name = "segredo = \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        @DisplayName("Deve rejeitar segredo nulo, vazio ou em branco")
        void shouldRejectNullOrBlankSecret(String secret) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(BLANK_SECRET_MESSAGE, exception.getMessage());
        }

        @ParameterizedTest(name = "segredo = \"{0}\"")
        @ValueSource(strings = {"###", "abc", "not valid base64 !!!"})
        @DisplayName("Deve rejeitar segredo que não é Base64 válido")
        void shouldRejectSecretThatIsNotValidBase64(String secret) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(INVALID_SECRET_MESSAGE, exception.getMessage());
            assertTrue(
                    exception.getCause() instanceof DecodingException
                            || exception.getCause() instanceof WeakKeyException,
                    "A causa deve ser DecodingException ou WeakKeyException"
            );
        }

        @ParameterizedTest(name = "chave com {0} byte(s)")
        @ValueSource(ints = {1, 16, 31})
        @DisplayName("Deve rejeitar chave com menos de 256 bits")
        void shouldRejectWeakKey(int sizeInBytes) {
            String secret = base64OfBytes(sizeInBytes);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> serviceOf(secret, EXPIRATION_IN_SECONDS, ISSUER)
            );

            assertEquals(INVALID_SECRET_MESSAGE, exception.getMessage());
            assertInstanceOf(WeakKeyException.class, exception.getCause());
        }

        @Test
        @DisplayName("Não deve expor o segredo na mensagem de erro")
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
    @DisplayName("Ao gerar um token JWT")
    class GenerateTokenTests {

        @Test
        @DisplayName("Deve gerar um JWT compacto com três partes")
        void shouldGenerateCompactJwtWithThreeParts() {
            String token = jwtService.generateToken(user);

            assertNotNull(token);
            assertFalse(token.isBlank());
            assertEquals(3, token.split("\\.").length);
        }

        @Test
        @DisplayName("Deve usar o username como subject e o issuer configurado")
        void shouldSetSubjectAndIssuer() {
            Claims claims = parse(jwtService.generateToken(user)).getPayload();

            assertEquals(USERNAME, claims.getSubject());
            assertEquals(ISSUER, claims.getIssuer());
        }

        @Test
        @DisplayName("Deve definir iat próximo ao momento da geração")
        void shouldSetIssuedAtCloseToNow() {
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            String token = jwtService.generateToken(user);

            Instant after = Instant.now();
            Instant issuedAt = parse(token).getPayload().getIssuedAt().toInstant();

            assertFalse(issuedAt.isBefore(before), "iat não pode ser anterior à geração");
            assertFalse(issuedAt.isAfter(after), "iat não pode ser posterior à geração");
        }

        @ParameterizedTest(name = "expiração = {0}s")
        @ValueSource(longs = {60, 3600, 86400})
        @DisplayName("Deve respeitar a expiração configurada")
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
        @DisplayName("Deve assinar o token com HMAC SHA-256")
        void shouldSignTokenWithHs256() {
            Jws<Claims> jws = parse(jwtService.generateToken(user));

            assertEquals("HS256", jws.getHeader().getAlgorithm());
        }

        @Test
        @DisplayName("Não deve incluir dados sensíveis no payload")
        void shouldContainOnlyStandardClaims() {
            Claims claims = parse(jwtService.generateToken(user)).getPayload();

            assertEquals(Set.of("sub", "iss", "iat", "exp"), claims.keySet());
        }

        @Test
        @DisplayName("Deve gerar tokens diferentes para usuários diferentes")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            String first = jwtService.generateToken(createUser("usuario.um"));
            String second = jwtService.generateToken(createUser("usuario.dois"));

            assertNotEquals(first, second);
        }
    }

    @Nested
    @DisplayName("Ao extrair o username de um token JWT")
    class ExtractUsernameTests {

        @Test
        @DisplayName("Deve extrair o username de um token válido")
        void shouldExtractUsernameFromValidToken() {
            String token = jwtService.generateToken(user);

            assertEquals(USERNAME, jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Deve validar token emitido por outra instância com a mesma configuração")
        void shouldAcceptTokenIssuedByAnotherInstanceWithSameConfiguration() {
            JwtService issuer = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);
            JwtService validator = serviceOf(SECRET, EXPIRATION_IN_SECONDS, ISSUER);

            String token = issuer.generateToken(user);

            assertEquals(USERNAME, validator.extractUsername(token));
        }

        @Test
        @DisplayName("Deve rejeitar token expirado")
        void shouldRejectExpiredToken() {
            JwtService service = serviceOf(SECRET, -60, ISSUER);
            String token = service.generateToken(user);

            assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Deve rejeitar token com payload adulterado")
        void shouldRejectTokenWithTamperedPayload() {
            String[] parts = jwtService.generateToken(user).split("\\.");
            long exp = Instant.now().plusSeconds(3600).getEpochSecond();
            String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"admin\",\"iss\":\"" + ISSUER + "\",\"exp\":" + exp + "}")
                            .getBytes(StandardCharsets.UTF_8)
            );
            String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

            assertThrows(SignatureException.class, () -> jwtService.extractUsername(forgedToken));
        }

        @Test
        @DisplayName("Deve rejeitar token assinado com outra chave")
        void shouldRejectTokenSignedWithAnotherKey() {
            JwtService foreign = serviceOf(OTHER_SECRET, EXPIRATION_IN_SECONDS, ISSUER);
            String token = foreign.generateToken(user);

            assertThrows(SignatureException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Deve rejeitar token com issuer diferente")
        void shouldRejectTokenWithDifferentIssuer() {
            JwtService foreign = serviceOf(SECRET, EXPIRATION_IN_SECONDS, "outro-emissor");
            String token = foreign.generateToken(user);

            assertThrows(IncorrectClaimException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Deve rejeitar token sem issuer")
        void shouldRejectTokenWithoutIssuer() {
            String token = Jwts.builder()
                    .subject(USERNAME)
                    .expiration(inOneHour())
                    .signWith(key(SECRET))
                    .compact();

            assertThrows(MissingClaimException.class, () -> jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("Deve rejeitar token sem assinatura")
        void shouldRejectUnsignedToken() {
            String token = Jwts.builder()
                    .subject(USERNAME)
                    .issuer(ISSUER)
                    .expiration(inOneHour())
                    .compact();

            assertThrows(UnsupportedJwtException.class, () -> jwtService.extractUsername(token));
        }

        @ParameterizedTest(name = "token = \"{0}\"")
        @ValueSource(strings = {"not-a-jwt", "a.b.c", "a.b", "..", "Bearer token"})
        @DisplayName("Deve rejeitar token malformado")
        void shouldRejectMalformedToken(String token) {
            assertThrows(JwtException.class, () -> jwtService.extractUsername(token));
        }

        @ParameterizedTest(name = "token = \"{0}\"")
        @NullAndEmptySource
        @DisplayName("Deve rejeitar token nulo ou vazio")
        void shouldRejectNullOrEmptyToken(String token) {
            assertThrows(IllegalArgumentException.class, () -> jwtService.extractUsername(token));
        }
    }

    private static JwtService serviceOf(String secret, long expirationInSeconds, String issuer) {
        return new JwtService(new JwtProperties(secret, expirationInSeconds, issuer));
    }

    private static User createUser(String username) {
        return new User(
                username,
                username + "@email.com",
                "encoded-password",
                Role.EMPLOYEE
        );
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

    private static Date inOneHour() {
        return Date.from(Instant.now().plusSeconds(3600));
    }

    private static String base64OfBytes(int sizeInBytes) {
        byte[] bytes = new byte[sizeInBytes];
        Arrays.fill(bytes, (byte) 1);
        return Base64.getEncoder().encodeToString(bytes);
    }
}