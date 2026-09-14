package com.lucas.sistemabancario.exception.handler;

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
    public String handleClientNotFound(ClientNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ClientCpfAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleClientCpfAlreadyExists(ClientCpfAlreadyExistsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleAccountNotFound(AccountNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleAccountAlreadyExists(AccountAlreadyExistsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountIsNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleAccountIsNotActive(AccountIsNotActiveException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountHasBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleAccountHasBalance(AccountHasBalanceException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountsAreSameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleAccountsAreSame(AccountsAreSameException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountIsNotSavingsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleAccountIsNotSavings(AccountIsNotSavingsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InvalidAccountTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidAccountType(InvalidAccountTypeException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidAmount(InvalidAmountException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleInsufficientBalance(InsufficientBalanceException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(YieldNotAvailableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleYieldNotAvailable(YieldNotAvailableException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(YieldAlreadyAppliedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleYieldAlreadyApplied(YieldAlreadyAppliedException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleUsernameAlreadyExists(UsernameAlreadyExistsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleUserEmailAlreadyExists(UserEmailAlreadyExistsException exception) {
        return exception.getMessage();
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
    public String handleHttpMessageNotReadable(){
        return "Dados da requisição inválidos.";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("uk_transaction_daily_yield")) {
            return "O rendimento já foi aplicado para esta conta hoje.";
        }
        return "Erro de integridade de dados no banco";
    }
}
