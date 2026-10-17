package com.lcsalvess.bankingsystem.service.transaction;

public enum DailyYieldProcessingResult {
    APPLIED,
    ALREADY_PROCESSED,
    NOT_ELIGIBLE,
    NO_YIELD_AVAILABLE,
    BALANCE_LIMIT_REACHED
}