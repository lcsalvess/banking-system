package com.lcsalvess.bankingsystem.event.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.event.client.ClientOperationEvent;
import com.lcsalvess.bankingsystem.event.client.ClientOperationType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.event.user.UserRoleChangedEvent;
import com.lcsalvess.bankingsystem.service.audit.recording.AuditService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Records the audit event in BEFORE_COMMIT, within the operation's transaction.
 * Existing logging listeners remain in AFTER_COMMIT: logging is informational,
 * while audit records must not be omitted from a committed operation.
 */
@Component
public class AuditEventListener {

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleUserOperation(UserOperationEvent event) {
        auditService.recordSuccess(
                toAuditType(event.type()),
                AuditEntityType.USER,
                String.valueOf(event.userId())
        );
    }

    // The previous and new roles are stored in the reason (e.g., EMPLOYEE_TO_AUDITOR),
    // allowing the audit trail to show what changed, not just that a change occurred.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleUserRoleChange(UserRoleChangedEvent event) {
        auditService.recordSuccess(
                AuditEventType.USER_ROLE_CHANGED,
                AuditEntityType.USER,
                String.valueOf(event.userId()),
                event.previousRole().name() + "_TO_" + event.newRole().name()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleClientOperation(ClientOperationEvent event) {
        auditService.recordSuccess(
                toAuditType(event.type()),
                AuditEntityType.CLIENT,
                String.valueOf(event.clientId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleAccountOperation(AccountOperationEvent event) {
        auditService.recordSuccess(
                toAuditType(event.type()),
                AuditEntityType.ACCOUNT,
                String.valueOf(event.accountId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionOperation(TransactionOperationEvent event) {
        if (event.type() == TransactionType.YIELD) {
            auditService.recordSystemSuccess(
                    AuditEventType.YIELD,
                    AuditEntityType.TRANSACTION,
                    Objects.requireNonNull(
                            event.transactionCode(),
                            "The yield transaction code is required."
                    ).toString()
            );
            return;
        }

        auditService.recordSuccess(
                toAuditType(event.type()),
                AuditEntityType.TRANSACTION,
                event.transactionCode().toString()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionTransfer(TransactionTransferEvent event) {
        auditService.recordSuccess(
                AuditEventType.TRANSFER,
                AuditEntityType.TRANSFER,
                event.transferCode().toString()
        );
    }

    private AuditEventType toAuditType(UserOperationType type) {
        return switch (type) {
            case CREATED -> AuditEventType.USER_CREATED;
            case DEACTIVATED -> AuditEventType.USER_DEACTIVATED;
            case REACTIVATED -> AuditEventType.USER_REACTIVATED;
            case PASSWORD_CHANGED -> AuditEventType.USER_PASSWORD_CHANGED;
        };
    }

    private AuditEventType toAuditType(ClientOperationType type) {
        return switch (type) {
            case CREATED -> AuditEventType.CLIENT_CREATED;
            case UPDATED -> AuditEventType.CLIENT_UPDATED;
        };
    }

    private AuditEventType toAuditType(AccountOperationType type) {
        return switch (type) {
            case CREATED -> AuditEventType.ACCOUNT_CREATED;
            case CANCELLED -> AuditEventType.ACCOUNT_CANCELLED;
        };
    }

    private AuditEventType toAuditType(TransactionType type) {
        return switch (type) {
            case DEPOSIT -> AuditEventType.DEPOSIT;
            case WITHDRAWAL -> AuditEventType.WITHDRAWAL;
            default -> throw new IllegalStateException(
                    "Transaction type is not auditable: " + type
            );
        };
    }
}
