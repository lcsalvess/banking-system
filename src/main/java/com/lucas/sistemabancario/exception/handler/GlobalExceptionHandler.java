package com.lucas.sistemabancario.exception.handler;

import com.lucas.sistemabancario.dto.response.ErrorResponse;
import com.lucas.sistemabancario.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String DUPLICATE_YIELD_CONSTRAINT = "uk_transaction_daily_yield";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorResponse error = new ErrorResponse(exception.getStatus().value(), exception.getMessage());
        return ResponseEntity
                .status(exception.getStatus())
                .body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Usuário ou senha inválidos.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return errors;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHttpMessageNotReadable() {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Dados da requisição inválidos.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains(DUPLICATE_YIELD_CONSTRAINT)) {
            return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "O rendimento já foi aplicado para esta conta hoje.");
        }
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Erro de integridade de dados no banco.");
    }
}
