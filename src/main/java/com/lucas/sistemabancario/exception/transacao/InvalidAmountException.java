package com.lucas.sistemabancario.exception.transacao;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
