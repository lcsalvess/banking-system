package com.lucas.sistemabancario.exception.transaction;

public class YieldNotAvailableException extends RuntimeException {
    public YieldNotAvailableException(String message) {
        super(message);
    }
}
