package com.lcsalvess.bankingsystem.entity.audit;

public enum AuditEventType {
    USER_CREATED,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    USER_PASSWORD_CHANGED,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
