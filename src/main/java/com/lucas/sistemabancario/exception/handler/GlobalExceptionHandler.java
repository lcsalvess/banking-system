package com.lucas.sistemabancario.exception.handler;

import com.lucas.sistemabancario.exception.cliente.ClienteCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.cliente.ClienteNotFoundException;
import com.lucas.sistemabancario.exception.conta.*;
import com.lucas.sistemabancario.exception.transacao.RendimentoJaAplicadoException;
import com.lucas.sistemabancario.exception.transacao.RendimentoNaoDisponivelException;
import com.lucas.sistemabancario.exception.transacao.SaldoIsNotEnoughException;
import com.lucas.sistemabancario.exception.transacao.ValorInvalidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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
    @ExceptionHandler(ClienteNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String tratarClientNotFound(ClienteNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String tratarContaNotFound(ContaNotFoundException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContaAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaAlreadyExists(ContaAlreadyExistsException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContaIsNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaIsNotActive(ContaIsNotActiveException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContaHasBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaHasBalance(ContaHasBalanceException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ValorInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarValorInvalido(ValorInvalidoException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(SaldoIsNotEnoughException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarSaldoIsNotEnough(SaldoIsNotEnoughException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContasIguaisException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContasIguais(ContasIguaisException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ContaIsNotPoupancaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarContaIsNotPoupanca(ContaIsNotPoupancaException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(RendimentoNaoDisponivelException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarRendimentoNaoDisponivel(RendimentoNaoDisponivelException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(RendimentoJaAplicadoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarRendimentoJaAplicado(RendimentoJaAplicadoException exception) {
        return exception.getMessage();
    }

    @ExceptionHandler(ClienteCpfAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String tratarCpfAlreadyExists(ClienteCpfAlreadyExistsException exception) {return exception.getMessage();}

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("idx_unico_rendimento_diario")) {
            return "O rendimento já foi aplicado para esta conta hoje.";
        }
        return "Erro de integridade de dados no banco";
    }
}
