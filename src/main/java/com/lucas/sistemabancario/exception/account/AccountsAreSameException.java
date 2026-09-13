package com.lucas.sistemabancario.exception.account;

public class AccountsAreSameException extends RuntimeException {
    public AccountsAreSameException(String message) {
        super(message);
    }
}
