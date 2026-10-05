package com.lucas.bankingsystem.exception.handler;

import com.lucas.bankingsystem.dto.response.exception.ErrorResponse;
import com.lucas.bankingsystem.dto.response.exception.ValidationErrorResponse;
import com.lucas.bankingsystem.exception.BusinessException;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
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
    private static final String DUPLICATE_YIELD_CONSTRAINT = "uk_transactions_daily_yield";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorResponse error = new ErrorResponse(exception.getStatus().value(), exception.getMessage());
        log.warn("Business exception: status={}, message={}",
                exception.getStatus().value(),
                exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        log.warn("Authentication failed: invalid username or password");
        return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Usuário ou senha inválidos.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleConstraintViolation(
            ConstraintViolationException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath()
                    .toString()
                    .substring(violation.getPropertyPath().toString().lastIndexOf('.') + 1);

            errors.put(field, violation.getMessage());
        });

        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação.",
                errors
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAuthorizationDenied() {
        log.warn("Authorization denied: access forbidden");
        return new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Acesso negado.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains(DUPLICATE_YIELD_CONSTRAINT)) {
            log.warn("Daily yield already applied");
            return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "O rendimento já foi aplicado para esta conta hoje.");
        }
        log.warn("Data integrity violation", exception);
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Erro de integridade de dados no banco.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(status.value(), "Dados da requisição inválidos.");

        return handleExceptionInternal(ex, error, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ValidationErrorResponse response = new ValidationErrorResponse(
                status.value(),
                "Erro de validação.",
                errors
        );

        return handleExceptionInternal(ex, response, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                "Parâmetro de requisição obrigatório ausente."
        );

        return handleExceptionInternal(ex, error, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex,
            @NonNull HttpHeaders headers,
            HttpStatusCode status,
            @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                status.value(),
                "Parâmetro de requisição inválido."
        );

        return handleExceptionInternal(ex, error, headers, status, request);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedRuntimeException(RuntimeException exception) {
        log.error("Unexpected error occurred", exception);

        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocorreu um erro interno no servidor."
        );
    }

    @ExceptionHandler(AddressProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleAddressProviderUnavailable(AddressProviderUnavailableException ex) {
        log.error("Address provider unavailable", ex);

        return new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "O serviço de consulta de endereços está temporariamente indisponível."
        );
    }
}
