package com.lcsalvess.bankingsystem.exception.audit;

import com.lcsalvess.bankingsystem.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidAuditEventQuerySnapshotException extends BusinessException {

    public InvalidAuditEventQuerySnapshotException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
