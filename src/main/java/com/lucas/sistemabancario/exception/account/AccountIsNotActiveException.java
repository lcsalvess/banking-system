package com.lucas.sistemabancario.exception.account;

public class AccountIsNotActiveException extends RuntimeException {
    public AccountIsNotActiveException(String message) {
        super(message);
    }
}
