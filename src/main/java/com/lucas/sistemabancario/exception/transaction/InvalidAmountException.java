package com.lucas.sistemabancario.exception.transaction;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
