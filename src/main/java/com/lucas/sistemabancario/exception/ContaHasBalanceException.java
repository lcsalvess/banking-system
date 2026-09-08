package com.lucas.sistemabancario.exception;

public class ContaHasBalanceException extends RuntimeException {
    public ContaHasBalanceException(String message) {
        super(message);
    }
}
