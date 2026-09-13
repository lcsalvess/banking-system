package com.lucas.sistemabancario.exception.transaction;

public class InterestNotAvailableException extends RuntimeException {
    public InterestNotAvailableException(String message) {
        super(message);
    }
}
