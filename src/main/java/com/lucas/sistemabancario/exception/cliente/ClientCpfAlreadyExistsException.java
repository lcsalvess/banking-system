package com.lucas.sistemabancario.exception.cliente;

public class ClientCpfAlreadyExistsException extends RuntimeException {
    public ClientCpfAlreadyExistsException(String message) {
        super(message);
    }
}
