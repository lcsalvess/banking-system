package com.lucas.sistemabancario.exception.transaction;

public class InterestAlreadyAppliedException extends RuntimeException {
    public InterestAlreadyAppliedException(String message) {
        super(message);
    }
}
