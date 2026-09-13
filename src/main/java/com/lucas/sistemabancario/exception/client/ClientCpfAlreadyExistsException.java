package com.lucas.sistemabancario.exception.client;

public class ClientCpfAlreadyExistsException extends RuntimeException {
    public ClientCpfAlreadyExistsException(String message) {
        super(message);
    }
}
