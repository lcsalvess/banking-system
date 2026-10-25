package com.lcsalvess.bankingsystem.exception.security;

public enum RateLimitScope {

    CLIENT_IP("client_ip"),

    USERNAME_AND_IP("username_ip"),

    USERNAME("username"),

    USER_ID("user_id");

    private final String metricTag;

    RateLimitScope(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}