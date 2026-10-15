package com.lcsalvess.bankingsystem.unit.exception.handler;


import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.handler.GlobalExceptionHandler;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTests.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        GlobalExceptionHandler.class,
        GlobalExceptionHandlerTests.TestController.class
})
class GlobalExceptionHandlerTests {

    private static final String THROW_URL = "/__test/throw";

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR =
            VALIDATOR_FACTORY.getValidator();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestController testController;

    @BeforeEach
    void clearConfiguredException() {
        testController.clearThrowable();
    }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    // -------------------------------------------------------------------------
    // AUTENTICAÇÃO
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid()
            throws Exception {

        performThrowing(new BadCredentialsException("Sensitive detail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INVALID_CREDENTIALS))
                .andExpect(content().string(
                        not(containsString("Sensitive detail"))
                ));
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticationFails()
            throws Exception {

        performThrowing(
                new AuthenticationServiceException("Internal auth detail")
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.AUTHENTICATION_FAILED))
                .andExpect(content().string(
                        not(containsString("Internal auth detail"))
                ));
    }

    // -------------------------------------------------------------------------
    // AUTORIZAÇÃO
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnForbiddenWhenAccessIsDenied()
            throws Exception {

        performThrowing(
                new AccessDeniedException("Sensitive authorization detail")
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.ACCESS_DENIED_RESOURCE))
                .andExpect(content().string(
                        not(containsString("Sensitive authorization detail"))
                ));
    }

    // -------------------------------------------------------------------------
    // REGRAS DE NEGÓCIO
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnStatusAndMessageDefinedByBusinessException()
            throws Exception {

        performThrowing(
                new TestBusinessException(
                        "Operação de negócio inválida.",
                        HttpStatus.UNPROCESSABLE_CONTENT
                )
        )
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message")
                        .value("Operação de negócio inválida."));
    }

    // -------------------------------------------------------------------------
    // INTEGRIDADE DE DADOS
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("knownConstraintCases")
    void shouldReturnConflictWhenKnownConstraintIsViolated(
            DatabaseConstraint constraint,
            String expectedMessage
    ) throws Exception {

        var cause = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate key",
                null,
                constraint.getConstraintName()
        );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Database detail",
                        cause
                );

        performThrowing(exception)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> knownConstraintCases() {
        return Stream.of(
                Arguments.of(
                        DatabaseConstraint.CLIENT_EMAIL_UNIQUE,
                        ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS
                ),
                Arguments.of(
                        DatabaseConstraint.CLIENT_CPF_UNIQUE,
                        ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS
                ),
                Arguments.of(
                        DatabaseConstraint.TRANSACTION_DAILY_YIELD_UNIQUE,
                        ApiErrorMessages.DAILY_YIELD_ALREADY_APPLIED
                ),
                Arguments.of(
                        DatabaseConstraint.USER_USERNAME_UNIQUE,
                        ApiErrorMessages.USERNAME_ALREADY_EXISTS
                ),
                Arguments.of(
                        DatabaseConstraint.USER_EMAIL_UNIQUE,
                        ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS
                ),
                Arguments.of(
                        DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE,
                        ApiErrorMessages.ACTIVE_ACCOUNT_ALREADY_EXISTS
                ),
                Arguments.of(
                        DatabaseConstraint.ACCOUNT_BALANCE_MAX,
                        ApiErrorMessages.BALANCE_LIMIT_EXCEEDED
                )
        );
    }

    @Test
    void shouldReturnInternalServerErrorWhenConstraintIsUnknown()
            throws Exception {

        var cause = new org.hibernate.exception.ConstraintViolationException(
                "Unknown constraint",
                null,
                "uk_unknown_constraint"
        );

        performThrowing(
                new DataIntegrityViolationException(
                        "Sensitive database detail",
                        cause
                )
        )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.DATABASE_INTEGRITY_ERROR))
                .andExpect(content().string(
                        not(containsString("Sensitive database detail"))
                ));
    }

    // -------------------------------------------------------------------------
    // VALIDAÇÃO DO CORPO DA REQUISIÇÃO
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsInvalid()
            throws Exception {

        mockMvc.perform(post("/__test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "age": 12
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.VALIDATION_ERROR))
                .andExpect(jsonPath("$.errors").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.age").exists());
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformed()
            throws Exception {

        mockMvc.perform(post("/__test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INVALID_REQUEST_DATA));
    }

    // -------------------------------------------------------------------------
    // VIOLAÇÕES DE CONSTRAINTS
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnBadRequestWhenNestedConstraintsAreViolated()
            throws Exception {

        Set<ConstraintViolation<?>> violations = new HashSet<>(
                VALIDATOR.validate(
                        new TestOuter(
                                new TestInner(""),
                                null
                        )
                )
        );

        jakarta.validation.ConstraintViolationException exception =
                new jakarta.validation.ConstraintViolationException(
                        "Validation failed",
                        violations
                );

        performThrowing(exception)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.VALIDATION_ERROR))
                .andExpect(jsonPath("$.errors").exists())
                .andExpect(jsonPath("$.errors.value").exists())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    // -------------------------------------------------------------------------
    // ERROS DE PARÂMETROS E ROTEAMENTO
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnBadRequestWhenRequiredParameterIsMissing()
            throws Exception {

        mockMvc.perform(get("/__test/required-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.MISSING_REQUIRED_PARAMETER));
    }

    @Test
    void shouldReturnBadRequestWhenParameterHasInvalidType()
            throws Exception {

        mockMvc.perform(get("/__test/type-mismatch")
                        .param("id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INVALID_REQUEST_PARAMETER));
    }

    @Test
    void shouldReturnNotFoundWhenResourceDoesNotExist()
            throws Exception {

        mockMvc.perform(get("/__test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.RESOURCE_NOT_FOUND));
    }

    @Test
    void shouldReturnMethodNotAllowedWhenHttpMethodIsUnsupported()
            throws Exception {

        mockMvc.perform(get("/__test/post-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.METHOD_NOT_ALLOWED));
    }

    @Test
    void shouldReturnUnsupportedMediaTypeWhenContentTypeIsUnsupported()
            throws Exception {

        mockMvc.perform(post("/__test/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid content type"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.UNSUPPORTED_MEDIA_TYPE));
    }

    // -------------------------------------------------------------------------
    // INFRAESTRUTURA
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnServiceUnavailableWhenAddressProviderIsUnavailable()
            throws Exception {

        performThrowing(
                new AddressProviderUnavailableException(
                        "Sensitive provider detail"
                )
        )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE))
                .andExpect(content().string(
                        not(containsString("Sensitive provider detail"))
                ));
    }

    @Test
    void shouldReturnServiceUnavailableWhenDatabaseLockCannotBeAcquired()
            throws Exception {

        performThrowing(
                new CannotAcquireLockException(
                        "Sensitive database lock detail"
                )
        )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE))
                .andExpect(content().string(
                        not(containsString("Sensitive database lock detail"))
                ));
    }

    // -------------------------------------------------------------------------
    // EXCEÇÕES INESPERADAS
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException()
            throws Exception {

        performThrowing(
                new IllegalStateException("Sensitive internal detail")
        )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value(ApiErrorMessages.INTERNAL_SERVER_ERROR))
                .andExpect(content().string(
                        not(containsString("Sensitive internal detail"))
                ));
    }

    // -------------------------------------------------------------------------
    // MÉTODOS AUXILIARES
    // -------------------------------------------------------------------------

    private ResultActions performThrowing(Throwable throwable)
            throws Exception {

        testController.setThrowable(throwable);

        try {
            return mockMvc.perform(get(THROW_URL));
        } finally {
            testController.clearThrowable();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(
            Throwable throwable
    ) throws T {
        throw (T) throwable;
    }

    private static class TestBusinessException extends BusinessException {

        TestBusinessException(String message, HttpStatus status) {
            super(message, status);
        }
    }

    // -------------------------------------------------------------------------
    // CONTROLLER UTILIZADO EXCLUSIVAMENTE NOS TESTES
    // -------------------------------------------------------------------------

    @RestController
    static class TestController {

        private volatile Throwable configuredThrowable;

        void setThrowable(Throwable throwable) {
            this.configuredThrowable = java.util.Objects.requireNonNull(
                    throwable
            );
        }

        void clearThrowable() {
            this.configuredThrowable = null;
        }

        @GetMapping("/__test/throw")
        String throwConfigured() {
            Throwable throwable = configuredThrowable;

            if (throwable == null) {
                throw new IllegalStateException(
                        "Nenhuma exceção foi configurada para o teste."
                );
            }

            sneakyThrow(throwable);

            return "unreachable";
        }

        @SuppressWarnings("unused")
        @PostMapping(
                value = "/__test/validated",
                consumes = MediaType.APPLICATION_JSON_VALUE
        )
        String validated(@Valid @RequestBody TestRequest request) {
            return "valid";
        }

        @GetMapping("/__test/required-parameter")
        String requiredParameter(
                @RequestParam("name") String name
        ) {
            return name;
        }

        @GetMapping("/__test/type-mismatch")
        String typeMismatch(
                @RequestParam("id") Integer id
        ) {
            return id.toString();
        }

        @PostMapping("/__test/post-only")
        String postOnly() {
            return "ok";
        }
    }

    // -------------------------------------------------------------------------
    // OBJETOS DE TESTE PARA VALIDAÇÃO
    // -------------------------------------------------------------------------

    private record TestRequest(
            @NotBlank
            @Size(max = 5)
            @Pattern(regexp = "[a-z]+")
            String name,

            @NotNull
            @Min(18)
            Integer age
    ) {
    }

    private record TestInner(
            @NotBlank
            @Size(min = 3)
            @Pattern(regexp = "\\d+")
            String value
    ) {
    }

    private record TestOuter(
            @Valid TestInner inner,

            @NotBlank String code
    ) {
    }
}