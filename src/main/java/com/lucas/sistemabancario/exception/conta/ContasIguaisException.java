package com.lucas.sistemabancario.exception.conta;

public class ContasIguaisException extends RuntimeException {
    public ContasIguaisException(String message) {
        super(message);
    }
}
