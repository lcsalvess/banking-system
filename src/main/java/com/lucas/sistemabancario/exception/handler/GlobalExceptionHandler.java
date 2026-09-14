package com.lucas.sistemabancario.exception.handler;

import com.lucas.sistemabancario.dto.response.ErrorResponse;
import com.lucas.sistemabancario.exception.account.*;
import com.lucas.sistemabancario.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.client.ClientNotFoundException;
import com.lucas.sistemabancario.exception.transaction.InsufficientBalanceException;
import com.lucas.sistemabancario.exception.transaction.InvalidAmountException;
import com.lucas.sistemabancario.exception.transaction.YieldAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transaction.YieldNotAvailableException;
import com.lucas.sistemabancario.exception.user.UserEmailAlreadyExistsException;
import com.lucas.sistemabancario.exception.user.UsernameAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
    @ExceptionHandler(ClientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleClientNotFound(ClientNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(ClientCpfAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleClientCpfAlreadyExists(ClientCpfAlreadyExistsException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleAccountNotFound(AccountNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAccountAlreadyExists(AccountAlreadyExistsException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountIsNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAccountIsNotActive(AccountIsNotActiveException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountHasBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAccountHasBalance(AccountHasBalanceException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountsAreSameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAccountsAreSame(AccountsAreSameException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(AccountIsNotSavingsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAccountIsNotSavings(AccountIsNotSavingsException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidAccountType(InvalidAccountTypeException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidAmount(InvalidAmountException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientBalance(InsufficientBalanceException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(YieldNotAvailableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleYieldNotAvailable(YieldNotAvailableException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(YieldAlreadyAppliedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleYieldAlreadyApplied(YieldAlreadyAppliedException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUsernameAlreadyExists(UsernameAlreadyExistsException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserEmailAlreadyExists(UserEmailAlreadyExistsException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        return new ErrorResponse("Usuário ou senha inválidos.");
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
        return new ErrorResponse("Dados da requisição inválidos.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("uk_transaction_daily_yield")) {
            return new ErrorResponse("O rendimento já foi aplicado para esta conta hoje.");
        }
        return new ErrorResponse("Erro de integridade de dados no banco.");
    }
}
