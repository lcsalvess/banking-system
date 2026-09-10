package com.lucas.sistemabancario.exception.transacao;

public class RendimentoNaoDisponivelException extends RuntimeException {
    public RendimentoNaoDisponivelException(String message) {
        super(message);
    }
}
