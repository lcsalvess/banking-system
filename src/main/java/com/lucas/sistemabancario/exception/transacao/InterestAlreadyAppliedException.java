package com.lucas.sistemabancario.exception.transacao;

public class InterestAlreadyAppliedException extends RuntimeException {
    public InterestAlreadyAppliedException(String message) {
        super(message);
    }
}
