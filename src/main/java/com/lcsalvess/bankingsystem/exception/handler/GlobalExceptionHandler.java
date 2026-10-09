package com.lcsalvess.bankingsystem.exception.handler;

import com.lcsalvess.bankingsystem.dto.response.exception.ErrorResponse;
import com.lcsalvess.bankingsystem.dto.response.exception.ValidationErrorResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //-------------- AUTENTICAÇÃO ---------

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        log.warn("Authentication failed: invalid credentials.");

        return new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorMessages.INVALID_CREDENTIALS
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthenticationException(
            AuthenticationException exception) {

        log.warn(
                "Authentication failed: exceptionType={}",
                exception.getClass().getSimpleName()
                );

        return new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ApiErrorMessages.AUTHENTICATION_FAILED
        );
    }

    //-------------- AUTORIZAÇÃO ---------

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAuthorizationDenied() {
        log.warn("Access denied: user is not authorized to perform the operation.");

        return new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ApiErrorMessages.ACCESS_DENIED_RESOURCE
        );
    }

    //-------------- REGRAS DE NEGÓCIO ---------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception) {

        ErrorResponse error = new ErrorResponse(
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
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        if (DatabaseConstraint.CLIENT_EMAIL_UNIQUE.isViolatedBy(exception)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            HttpStatus.CONFLICT.value(),
                            ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS
                    ));
        }

        if (DatabaseConstraint.CLIENT_CPF_UNIQUE.isViolatedBy(exception)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            HttpStatus.CONFLICT.value(),
                            ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS
                    ));
        }

        if (DatabaseConstraint.TRANSACTION_DAILY_YIELD_UNIQUE.isViolatedBy(exception)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            HttpStatus.CONFLICT.value(),
                            ApiErrorMessages.DAILY_YIELD_ALREADY_APPLIED
                    ));
        }

        log.warn("Data integrity violation.", exception);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ApiErrorMessages.DATABASE_INTEGRITY_ERROR
                ));
    }

    //-------------- VALIDAÇÃO / REQUISIÇÃO ---------

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleConstraintViolation(
            ConstraintViolationException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath()
                    .toString()
                    .substring(
                            violation.getPropertyPath().toString().lastIndexOf('.') + 1
                    );

            errors.put(field, violation.getMessage());
        });

        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorMessages.VALIDATION_ERROR,
                errors
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                ApiErrorMessages.INVALID_REQUEST_DATA
        );

        return handleExceptionInternal(
                ex,
                error,
                headers,
                status,
                request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        ValidationErrorResponse response = new ValidationErrorResponse(
                status.value(),
                ApiErrorMessages.VALIDATION_ERROR,
                errors
        );

        return handleExceptionInternal(
                ex,
                response,
                headers,
                status,
                request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                ApiErrorMessages.MISSING_REQUIRED_PARAMETER
        );

        return handleExceptionInternal(
                ex,
                error,
                headers,
                status,
                request
        );
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                ApiErrorMessages.INVALID_REQUEST_PARAMETER
        );

        return handleExceptionInternal(
                ex,
                error,
                headers,
                status,
                request
        );
    }

    //-------------- INFRAESTRUTURA ---------

    @ExceptionHandler(AddressProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleAddressProviderUnavailable(
            AddressProviderUnavailableException ex) {

        log.error("Address lookup provider unavailable.", ex);

        return new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE
        );
    }

    //-------------- EXCEÇÕES INESPERADAS ---------

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedRuntimeException(
            RuntimeException exception) {

        log.error("Unexpected error while processing request.", exception);

        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorMessages.INTERNAL_SERVER_ERROR
        );
    }
}