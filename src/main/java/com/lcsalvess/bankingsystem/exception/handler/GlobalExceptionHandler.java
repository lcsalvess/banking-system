package com.lcsalvess.bankingsystem.exception.handler;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.BusinessException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //-------------- AUTENTICAÇÃO ---------

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleBadCredentials() {
        log.warn("Authentication failed: invalid credentials.");

        return new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorMessages.INVALID_CREDENTIALS
        );
    }

    /**
     * Falha interna ao processar a autenticação (por exemplo, banco fora do ar durante
     * o login, que o Spring Security embrulha em InternalAuthenticationServiceException).
     * Não é problema de credencial, então não pode virar 401.
     */
    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationServiceException(
            AuthenticationServiceException exception) {

        if (isDatabaseFailure(exception)) {
            log.error("Authentication unavailable: database failure.", exception);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiErrorResponse(
                            HttpStatus.SERVICE_UNAVAILABLE.value(),
                            ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
                    ));
        }

        log.error("Authentication failed due to an internal error.", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ApiErrorMessages.INTERNAL_SERVER_ERROR
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleAuthenticationException(
            AuthenticationException exception) {

        log.warn(
                "Authentication failed: exceptionType={}",
                exception.getClass().getSimpleName()
        );

        return new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorMessages.AUTHENTICATION_FAILED
        );
    }

    //-------------- AUTORIZAÇÃO ---------

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleAuthorizationDenied() {
        log.warn("Access denied: user is not authorized to perform the operation.");

        return new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ApiErrorMessages.ACCESS_DENIED_RESOURCE
        );
    }

    //-------------- REGRAS DE NEGÓCIO ---------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
                exception.getStatus().value(),
                exception.getMessage()
        );

        log.warn(
                "Business exception: status={}, exceptionType={}",
                exception.getStatus().value(),
                exception.getClass().getSimpleName()
        );

        return ResponseEntity
                .status(exception.getStatus())
                .body(error);
    }

    //-------------- INTEGRIDADE DE DADOS ---------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        for (Map.Entry<DatabaseConstraint, String> entry
                : CONSTRAINT_ERROR_MESSAGES.entrySet()) {

            if (entry.getKey().isViolatedBy(exception)) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ApiErrorResponse(
                                HttpStatus.CONFLICT.value(),
                                entry.getValue()
                        ));
            }
        }

        // Não loga a exceção: o Detail do PostgreSQL traz os valores da linha
        // (saldos, CPF, hash de senha). Só o SQLState e o nome da constraint.
        log.warn(
                "Data integrity violation: sqlState={}, constraint={}",
                sqlStateOf(exception),
                constraintNameOf(exception)
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ApiErrorMessages.DATABASE_INTEGRITY_ERROR
                ));
    }


    //-------------- VALIDAÇÃO / REQUISIÇÃO ---------


    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(
            ConstraintViolationException exception) {

        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> requiredFields = new HashSet<>();

        exception.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();

            String field = propertyPath.substring(
                    propertyPath.lastIndexOf('.') + 1
            );

            String code = violation.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
                    .getSimpleName();

            String message = violation.getMessage();

            if (isRequiredValidation(code)) {
                errors.put(field, message);
                requiredFields.add(field);
            } else if (!requiredFields.contains(field)) {
                addValidationError(errors, field, message);
            }
        });

        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorMessages.VALIDATION_ERROR,
                errors
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        return buildErrorResponse(
                ex,
                headers,
                status,
                request,
                ApiErrorMessages.INVALID_REQUEST_DATA
        );
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        Set<String> requiredFields = new HashSet<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage() != null
                    ? error.getDefaultMessage()
                    : "Valor inválido";

            if (isRequiredValidation(error.getCode())) {
                errors.put(field, message);
                requiredFields.add(field);
            } else if (!requiredFields.contains(field)) {
                addValidationError(errors, field, message);
            }
        });

        ex.getBindingResult().getGlobalErrors().forEach(error ->
                addValidationError(
                        errors,
                        error.getObjectName(),
                        error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Objeto inválido"
                )
        );

        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                ApiErrorMessages.VALIDATION_ERROR,
                errors
        );

        return handleExceptionInternal(
                ex,
                body,
                headers,
                status,
                request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        return buildErrorResponse(
                ex,
                headers,
                status,
                request,
                ApiErrorMessages.MISSING_REQUIRED_PARAMETER
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        return buildErrorResponse(
                ex,
                headers,
                status,
                request,
                ApiErrorMessages.INVALID_REQUEST_PARAMETER
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
            @NonNull NoResourceFoundException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        return buildErrorResponse(
                ex,
                headers,
                status,
                request,
                ApiErrorMessages.RESOURCE_NOT_FOUND
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            @NonNull HttpRequestMethodNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        ApiErrorResponse error = new ApiErrorResponse(
                status.value(),
                ApiErrorMessages.METHOD_NOT_ALLOWED
        );

        return handleExceptionInternal(
                ex, error, headers, status, request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        return buildErrorResponse(
                ex,
                headers,
                status,
                request,
                ApiErrorMessages.UNSUPPORTED_MEDIA_TYPE
        );
    }

    //-------------- INFRAESTRUTURA ---------

    @ExceptionHandler(AddressProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleAddressProviderUnavailable(
            AddressProviderUnavailableException ex) {

        log.error("Address lookup provider unavailable.", ex);

        return new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE
        );
    }


    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            QueryTimeoutException.class,
            CannotCreateTransactionException.class
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleDatabaseUnavailable(Exception exception) {

        log.error("Database unavailable.", exception);

        return new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
        );
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handlePessimisticLockingFailure(
            PessimisticLockingFailureException exception) {

        log.warn("Could not acquire database lock.", exception);

        return new ApiErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
        );
    }


    //-------------- EXCEÇÕES INESPERADAS ---------

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedException(Exception exception) {

        log.error("Unexpected error while processing request.", exception);

        return new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorMessages.INTERNAL_SERVER_ERROR
        );
    }

    //-------------- MÉTODOS AUXILIARES ---------

    private @Nullable ResponseEntity<Object> buildErrorResponse(
            Exception ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request,
            String message) {

        ApiErrorResponse error = new ApiErrorResponse(
                status.value(),
                message
        );

        return handleExceptionInternal(
                ex,
                error,
                headers,
                status,
                request
        );
    }

    private static final Map<DatabaseConstraint, String> CONSTRAINT_ERROR_MESSAGES = Map.of(
            DatabaseConstraint.CLIENT_EMAIL_UNIQUE,
            ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS,

            DatabaseConstraint.CLIENT_CPF_UNIQUE,
            ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,

            DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE,
            ApiErrorMessages.ACTIVE_ACCOUNT_ALREADY_EXISTS,

            DatabaseConstraint.ACCOUNT_BALANCE_MAX,
            ApiErrorMessages.BALANCE_LIMIT_EXCEEDED,

            DatabaseConstraint.USER_USERNAME_UNIQUE,
            ApiErrorMessages.USERNAME_ALREADY_EXISTS,

            DatabaseConstraint.USER_EMAIL_UNIQUE,
            ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS,

            DatabaseConstraint.SYSTEM_MIGRATION_USER_INACTIVE,
            ApiErrorMessages.SYSTEM_USER_CANNOT_BE_REACTIVATED
    );

    private void addValidationError(
            Map<String, String> errors,
            String field,
            String message
    ) {
        errors.merge(field, message, (existing, next) -> existing + "; " + next);
    }


    private static boolean isDatabaseFailure(Throwable throwable) {
        for (Throwable cause = throwable;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof DataAccessException
                    || cause instanceof TransactionException) {
                return true;
            }
        }

        return false;
    }

    private static String sqlStateOf(Throwable throwable) {
        for (Throwable cause = throwable;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof org.hibernate.JDBCException jdbcException) {
                java.sql.SQLException sqlException =
                        jdbcException.getSQLException();

                if (sqlException != null
                        && sqlException.getSQLState() != null) {
                    return sqlException.getSQLState();
                }
            }

            if (cause instanceof java.sql.SQLException sqlException
                    && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }

        return "unknown";
    }

    private static String constraintNameOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "unknown";
    }

    private boolean isRequiredValidation(String code) {
        return Set.of("NotBlank", "NotNull", "NotEmpty").contains(code);
    }
}