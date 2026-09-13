package com.lucas.sistemabancario.exception.transaction;

public class YieldAlreadyAppliedException extends RuntimeException {
    public YieldAlreadyAppliedException(String message) {
        super(message);
    }
}
