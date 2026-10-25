package com.lcsalvess.bankingsystem.exception.security;

public enum RateLimitOperation {

    LOGIN("login"),

    PASSWORD_CHANGE("password_change");

    private final String metricTag;

    RateLimitOperation(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}