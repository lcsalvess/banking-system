package com.lucas.sistemabancario.exception.conta;

public class ContaIsNotActiveException extends RuntimeException {
    public ContaIsNotActiveException(String message) {
        super(message);
    }
}
