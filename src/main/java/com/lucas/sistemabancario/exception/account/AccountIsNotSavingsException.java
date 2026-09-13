package com.lucas.sistemabancario.exception.account;

public class AccountIsNotSavingsException extends RuntimeException {
    public AccountIsNotSavingsException(String message) {
        super(message);
    }
}
