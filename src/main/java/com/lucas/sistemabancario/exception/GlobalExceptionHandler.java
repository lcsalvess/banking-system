package com.lucas.sistemabancario.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
