package com.lucas.sistemabancario.exception.handler;

import com.lucas.sistemabancario.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.client.ClientNotFoundException;
import com.lucas.sistemabancario.exception.account.*;
import com.lucas.sistemabancario.exception.transaction.InterestAlreadyAppliedException;
import com.lucas.sistemabancario.exception.transaction.InterestNotAvailableException;
import com.lucas.sistemabancario.exception.transaction.InsufficientBalanceException;
import com.lucas.sistemabancario.exception.transaction.InvalidAmountException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
    public String tratarClientNotFound(ClientNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String tratarContaNotFound(AccountNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaAlreadyExists(AccountAlreadyExistsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountIsNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaIsNotActive(AccountIsNotActiveException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountHasBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaHasBalance(AccountHasBalanceException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarValorInvalido(InvalidAmountException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarSaldoIsNotEnough(InsufficientBalanceException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountsAreSameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContasIguais(AccountsAreSameException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(AccountIsNotSavingsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaIsNotPoupanca(AccountIsNotSavingsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InterestNotAvailableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarRendimentoNaoDisponivel(InterestNotAvailableException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(InterestAlreadyAppliedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarRendimentoJaAplicado(InterestAlreadyAppliedException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ClientCpfAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarCpfAlreadyExists(ClientCpfAlreadyExistsException exception) {return exception.getMessage();}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> tratarValidationExceptions(MethodArgumentNotValidException exception) {
        Map<String, String> erros = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(erro -> {
            String fieldName = erro.getField();
            String errorMessage = erro.getDefaultMessage();
            erros.put(fieldName, errorMessage);
        });
        return erros;
    }

    @ExceptionHandler(InvalidAccountTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidAccountType(InvalidAccountTypeException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("idx_unico_rendimento_diario")) {
            return "O rendimento já foi aplicado para esta conta hoje.";
        }
        return "Erro de integridade de dados no banco";
    }
}
