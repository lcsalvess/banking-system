package com.lucas.sistemabancario.exception.conta;

public class ContaAlreadyExistsException extends RuntimeException {
    public ContaAlreadyExistsException(String message) {
        super(message);
    }
}
