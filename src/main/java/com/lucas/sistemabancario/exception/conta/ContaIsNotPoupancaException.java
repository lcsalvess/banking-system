package com.lucas.sistemabancario.exception.conta;

public class ContaIsNotPoupancaException extends RuntimeException {
    public ContaIsNotPoupancaException(String message) {
        super(message);
    }
}
