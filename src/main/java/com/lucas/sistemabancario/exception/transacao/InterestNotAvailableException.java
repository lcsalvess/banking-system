package com.lucas.sistemabancario.exception.transacao;

public class InterestNotAvailableException extends RuntimeException {
    public InterestNotAvailableException(String message) {
        super(message);
    }
}
