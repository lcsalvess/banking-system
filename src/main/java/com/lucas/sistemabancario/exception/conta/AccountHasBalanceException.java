package com.lucas.sistemabancario.exception.conta;

public class AccountHasBalanceException extends RuntimeException {
    public AccountHasBalanceException(String message) {
        super(message);
    }
}
