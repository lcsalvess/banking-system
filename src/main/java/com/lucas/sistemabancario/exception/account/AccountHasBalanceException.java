package com.lucas.sistemabancario.exception.account;

public class AccountHasBalanceException extends RuntimeException {
    public AccountHasBalanceException(String message) {
        super(message);
    }
}
