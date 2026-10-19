package com.lcsalvess.bankingsystem.unit.bootstrap;

import com.lcsalvess.bankingsystem.bootstrap.AdminBootstrapRunner;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.bootstrap.AdminBootstrapRunnerTests#";

    private static final String VALID_USERNAME = "Admin.Root";
    private static final String VALID_EMAIL = "Admin@Example.com";
    private static final String VALID_PASSWORD = "S3nh@-Forte-123";
    private static final String ENCODED_PASSWORD = "encoded-password";

    private static final String INVALID_CREDENTIALS_PREFIX =
            "Credenciais do administrador inicial inválidas";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrapRunner runner;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        runner = new AdminBootstrapRunner(userRepository, passwordEncoder, validator);
    }

    @Nested
    @DisplayName("When an administrator already exists")
    class AdminAlreadyExistsTests {

        @Test
        @DisplayName("Should skip the bootstrap without touching credentials")
        void shouldSkipWhenAdminAlreadyExists() {
            configure(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, true);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

            runner.run();

            verify(userRepository).existsByRole(Role.ADMIN);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("Should skip even when the configured credentials are invalid")
        void shouldSkipWhenAdminExistsAndCredentialsAreInvalid() {
            configure("x".repeat(21), "invalid", "short", true);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

            runner.run();

            verify(userRepository).existsByRole(Role.ADMIN);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordEncoder);
        }
    }

    @Nested
    @DisplayName("When the initial credentials are not configured")
    class CredentialsNotConfiguredTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "missingCredentials")
        @DisplayName("Should skip with a warning when the bootstrap is not required")
        void shouldSkipWhenBootstrapIsNotRequired(
                String scenario, String username, String email, String password) {

            configure(username, email, password, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

            runner.run();

            verify(userRepository).existsByRole(Role.ADMIN);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordEncoder);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "missingCredentials")
        @DisplayName("Should fail the startup when the bootstrap is required")
        void shouldFailWhenBootstrapIsRequired(
                String scenario, String username, String email, String password) {

            configure(username, email, password, true);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, () -> runner.run());

            assertTrue(exception.getMessage().contains("ADMIN_USERNAME"));
            assertTrue(exception.getMessage().contains("ADMIN_EMAIL"));
            assertTrue(exception.getMessage().contains("ADMIN_PASSWORD"));

            verify(userRepository).existsByRole(Role.ADMIN);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordEncoder);
        }
    }

    @Nested
    @DisplayName("When the configured credentials are invalid")
    class InvalidCredentialsTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidCredentials")
        @DisplayName("Should fail the startup without encoding or saving")
        void shouldFailWhenCredentialsAreInvalid(
                String scenario,
                String username,
                String email,
                String password,
                String expectedMessage) {

            configure(username, email, password, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, () -> runner.run());

            assertTrue(exception.getMessage().startsWith(INVALID_CREDENTIALS_PREFIX));
            assertTrue(exception.getMessage().contains(expectedMessage));

            verify(userRepository).existsByRole(Role.ADMIN);
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("Should never expose the password in the exception message")
        void shouldNotExposePasswordInTheMessage() {
            String shortPassword = "abc";

            configure(VALID_USERNAME, VALID_EMAIL, shortPassword, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, () -> runner.run());

            assertFalse(exception.getMessage().contains(shortPassword));
        }

        @Test
        @DisplayName("Should report every violation at once")
        void shouldReportAllViolations() {
            configure("x".repeat(21), "invalid", "short", false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, () -> runner.run());

            assertTrue(exception.getMessage()
                    .contains(ValidationMessages.INVALID_USERNAME_LENGTH));
            assertTrue(exception.getMessage().contains(ValidationMessages.INVALID_EMAIL));
            assertTrue(exception.getMessage()
                    .contains(ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG));
        }
    }

    @Nested
    @DisplayName("When the configured credentials are valid")
    class ValidCredentialsTests {

        @Test
        @DisplayName("Should create the administrator with normalized identity and encoded password")
        void shouldCreateAdministrator() {
            configure(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, true);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            runner.run();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

            verify(userRepository).existsByRole(Role.ADMIN);
            verify(passwordEncoder).encode(VALID_PASSWORD);
            verify(userRepository).save(captor.capture());
            verifyNoMoreInteractions(userRepository, passwordEncoder);

            User saved = captor.getValue();

            assertEquals("admin.root", saved.getUsername());
            assertEquals("admin@example.com", saved.getEmail());
            assertEquals(ENCODED_PASSWORD, saved.getPassword());
            assertEquals(Role.ADMIN, saved.getRole());
        }

        @Test
        @DisplayName("Should create the administrator when the bootstrap is not required")
        void shouldCreateAdministratorWhenNotRequired() {
            configure(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            runner.run();

            verify(userRepository).existsByRole(Role.ADMIN);
            verify(passwordEncoder).encode(VALID_PASSWORD);
            verify(userRepository).save(any(User.class));
            verifyNoMoreInteractions(userRepository, passwordEncoder);
        }

        @Test
        @DisplayName("Should accept a password with exactly 64 characters")
        void shouldAcceptPasswordWithExactlyMaxCharacters() {
            String password = "Aa1!" + "a".repeat(60);

            assertEquals(64, password.length());

            configure(VALID_USERNAME, VALID_EMAIL, password, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordEncoder.encode(password)).thenReturn(ENCODED_PASSWORD);

            runner.run();

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should accept a username with exactly 20 characters")
        void shouldAcceptUsernameWithExactlyMaxLength() {
            String username = "a".repeat(20);

            configure(username, VALID_EMAIL, VALID_PASSWORD, false);
            when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
            when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            runner.run();

            verify(userRepository).save(any(User.class));
        }
    }

    // ---------- providers ----------

    @SuppressWarnings("unused")
    static Stream<Arguments> missingCredentials() {
        return Stream.of(
                Arguments.of("all null", null, null, null),
                Arguments.of("all empty", "", "", ""),
                Arguments.of("username blank", "   ", VALID_EMAIL, VALID_PASSWORD),
                Arguments.of("username missing", null, VALID_EMAIL, VALID_PASSWORD),
                Arguments.of("e-mail blank", VALID_USERNAME, "   ", VALID_PASSWORD),
                Arguments.of("e-mail missing", VALID_USERNAME, null, VALID_PASSWORD),
                Arguments.of("password blank", VALID_USERNAME, VALID_EMAIL, "   "),
                Arguments.of("password missing", VALID_USERNAME, VALID_EMAIL, null)
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidCredentials() {

        // 64 + 1 + 186 + 4 = 255 characters, exceeding the 254-character limit.
        String longEmail = "a".repeat(64) + "@" + "b".repeat(186) + ".com";

        return Stream.of(
                Arguments.of("username with 21 characters",
                        "a".repeat(21), VALID_EMAIL, VALID_PASSWORD, ValidationMessages.INVALID_USERNAME_LENGTH),
                Arguments.of("username with whitespace",
                        "admin root", VALID_EMAIL, VALID_PASSWORD, ValidationMessages.INVALID_USERNAME_FORMAT),
                Arguments.of("username with accented character",
                        "admín", VALID_EMAIL, VALID_PASSWORD, ValidationMessages.INVALID_USERNAME_FORMAT),
                Arguments.of("username with symbol",
                        "admin@root", VALID_EMAIL, VALID_PASSWORD, ValidationMessages.INVALID_USERNAME_FORMAT),
                Arguments.of("username reserved for the system",
                        "SYSTEM", VALID_EMAIL, VALID_PASSWORD, ValidationMessages.RESERVED_USERNAME),
                Arguments.of("e-mail without at sign",
                        VALID_USERNAME, "admin.example.com", VALID_PASSWORD, ValidationMessages.INVALID_EMAIL),
                Arguments.of("e-mail above 254 characters",
                        VALID_USERNAME, longEmail, VALID_PASSWORD, ValidationMessages.INVALID_EMAIL_LENGTH),
                Arguments.of("password with 7 characters",
                        VALID_USERNAME, VALID_EMAIL, "1234567", ValidationMessages.PASSWORD_TOO_SHORT_OR_LONG),
                Arguments.of("password with 73 bytes",
                        VALID_USERNAME, VALID_EMAIL, "a".repeat(73), ValidationMessages.PASSWORD_EXCEEDS_MAX_BYTE_LENGTH),
                Arguments.of("password with 74 bytes in 37 multibyte characters",
                        VALID_USERNAME, VALID_EMAIL, "é".repeat(37), ValidationMessages.PASSWORD_EXCEEDS_MAX_BYTE_LENGTH)
        );
    }

    // ---------- helpers ----------

    private void configure(
            String username, String email, String password, boolean bootstrapRequired) {

        ReflectionTestUtils.setField(runner, "adminUsername", username);
        ReflectionTestUtils.setField(runner, "adminEmail", email);
        ReflectionTestUtils.setField(runner, "adminPassword", password);
        ReflectionTestUtils.setField(runner, "bootstrapRequired", bootstrapRequired);
    }
}