package com.lcsalvess.bankingsystem.exception.security;

/**
 * Indica qual controle do limitador de login rejeitou a requisição.
 *
 * <p>O motivo é usado apenas internamente (logs e métricas) e nunca é
 * exposto na resposta HTTP.
 */
public enum LoginRateLimitReason {

    /** Bucket de requisições do endereço IP esgotado. */
    CLIENT_IP("client_ip"),

    /** Bloqueio da combinação nome de usuário + endereço IP. */
    USERNAME_AND_IP("username_ip"),

    /** Bloqueio do nome de usuário, independentemente do endereço IP. */
    USERNAME("username");

    private final String metricTag;

    LoginRateLimitReason(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}