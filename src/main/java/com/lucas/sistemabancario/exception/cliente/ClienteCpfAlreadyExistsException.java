package com.lucas.sistemabancario.exception.cliente;

public class ClienteCpfAlreadyExistsException extends RuntimeException {
    public ClienteCpfAlreadyExistsException(String message) {
        super(message);
    }
}
