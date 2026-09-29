package com.lucas.bankingsystem.exception.handler;

import com.lucas.bankingsystem.dto.response.exception.ErrorResponse;
import com.lucas.bankingsystem.dto.response.exception.ValidationErrorResponse;
import com.lucas.bankingsystem.exception.BusinessException;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String DUPLICATE_YIELD_CONSTRAINT = "uk_transaction_daily_yield";
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidationExceptions(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return new ValidationErrorResponse(HttpStatus.BAD_REQUEST.value(), "Erro de validação.", errors);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAuthorizationDenied() {
        log.warn("Authorization denied: access forbidden");
        return new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Acesso negado.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHttpMessageNotReadable() {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Dados da requisição inválidos.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentTypeMismatch() {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Parâmetro de requisição inválido.");
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParameter() {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Parâmetro de requisição obrigatório ausente.");
    }

    @ExceptionHandler(AddressProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleAddressProviderUnavailable() {
        log.error("Address provider unavailable");
        return new ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), "O serviço de consulta de endereços está temporariamente indisponível.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception exception) {
        log.error("Unexpected error occurred", exception);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Ocorreu um erro interno no servidor.");
    }
}
