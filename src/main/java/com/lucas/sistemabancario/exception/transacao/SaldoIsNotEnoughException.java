package com.lucas.sistemabancario.exception.transacao;

public class SaldoIsNotEnoughException extends RuntimeException {
    public SaldoIsNotEnoughException(String message) {
        super(message);
    }
}
