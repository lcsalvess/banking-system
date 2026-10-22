package com.lcsalvess.bankingsystem.event.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.service.audit.AuditService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Grava a auditoria em BEFORE_COMMIT, dentro da transação da operação. Os
 * listeners de log existentes continuam em AFTER_COMMIT: o log é só
 * informativo, a auditoria não pode ficar de fora de uma operação commitada.
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

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionOperation(TransactionOperationEvent event) {
        // O rendimento é uma transação por conta por dia, e o próprio
        // ledger já o marca com performed_by_system.
        if (event.type() == TransactionType.YIELD) {
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
