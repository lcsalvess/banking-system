package com.lucas.sistemabancario.exception.conta;

public class ContaHasBalanceException extends RuntimeException {
    public ContaHasBalanceException(String message) {
        super(message);
    }
}
