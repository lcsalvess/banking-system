package com.lucas.sistemabancario.exception.conta;

public class AccountIsNotSavingsException extends RuntimeException {
    public AccountIsNotSavingsException(String message) {
        super(message);
    }
}
