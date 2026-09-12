package com.lucas.sistemabancario.exception.conta;

public class AccountsAreSameException extends RuntimeException {
    public AccountsAreSameException(String message) {
        super(message);
    }
}
