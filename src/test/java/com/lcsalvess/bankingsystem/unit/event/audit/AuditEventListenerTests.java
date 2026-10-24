package com.lcsalvess.bankingsystem.unit.event.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.event.audit.AuditEventListener;
import com.lcsalvess.bankingsystem.event.account.AccountOperationEvent;
import com.lcsalvess.bankingsystem.event.account.AccountOperationType;
import com.lcsalvess.bankingsystem.event.client.ClientOperationEvent;
import com.lcsalvess.bankingsystem.event.client.ClientOperationType;
import com.lcsalvess.bankingsystem.event.transaction.TransactionOperationEvent;
import com.lcsalvess.bankingsystem.event.transaction.TransactionTransferEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationEvent;
import com.lcsalvess.bankingsystem.event.user.UserOperationType;
import com.lcsalvess.bankingsystem.service.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTests {

    private static final String USERNAME = "lucas";
    private static final String ACCOUNT_NUMBER = "123456";
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");

    @Mock
    private AuditService auditService;

    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(auditService);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "CREATED, USER_CREATED",
            "DEACTIVATED, USER_DEACTIVATED",
            "REACTIVATED, USER_REACTIVATED",
            "PASSWORD_CHANGED, USER_PASSWORD_CHANGED"
    })
    @DisplayName("Should audit user operations with the user id as the entity")
    void shouldAuditUserOperations(
            UserOperationType operation,
            AuditEventType expectedType
    ) {
        listener.handleUserOperation(new UserOperationEvent(42L, operation));

        verify(auditService).recordSuccess(
                expectedType,
                AuditEntityType.USER,
                "42"
        );
        verifyNoMoreInteractions(auditService);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "CREATED, CLIENT_CREATED",
            "UPDATED, CLIENT_UPDATED"
    })
    @DisplayName("Should audit client operations with the client id as the entity")
    void shouldAuditClientOperations(
            ClientOperationType operation,
            AuditEventType expectedType
    ) {
        listener.handleClientOperation(
                new ClientOperationEvent(7L, operation, USERNAME)
        );

        verify(auditService).recordSuccess(
                expectedType,
                AuditEntityType.CLIENT,
                "7"
        );
        verifyNoMoreInteractions(auditService);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "CREATED, ACCOUNT_CREATED",
            "CANCELLED, ACCOUNT_CANCELLED"
    })
    @DisplayName("Should audit account operations with the account id as the entity")
    void shouldAuditAccountOperations(
            AccountOperationType operation,
            AuditEventType expectedType
    ) {
        listener.handleAccountOperation(
                new AccountOperationEvent(9L, operation, USERNAME)
        );

        verify(auditService).recordSuccess(
                expectedType,
                AuditEntityType.ACCOUNT,
                "9"
        );
        verifyNoMoreInteractions(auditService);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DEPOSIT, DEPOSIT",
            "WITHDRAWAL, WITHDRAWAL"
    })
    @DisplayName("Should audit deposits and withdrawals with the transaction code as the entity")
    void shouldAuditTransactionOperations(
            TransactionType type,
            AuditEventType expectedType
    ) {
        UUID transactionCode = UUID.randomUUID();

        listener.handleTransactionOperation(
                new TransactionOperationEvent(
                        type,
                        ACCOUNT_NUMBER,
                        AMOUNT,
                        USERNAME,
                        transactionCode
                )
        );

        verify(auditService).recordSuccess(
                expectedType,
                AuditEntityType.TRANSACTION,
                transactionCode.toString()
        );
        verifyNoMoreInteractions(auditService);
    }

    @Test
    @DisplayName("Should not audit the daily yield")
    void shouldNotAuditDailyYield() {
        listener.handleTransactionOperation(
                new TransactionOperationEvent(
                        TransactionType.YIELD,
                        ACCOUNT_NUMBER,
                        AMOUNT,
                        "SYSTEM"
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Should audit a transfer once, with the transfer code as the entity")
    void shouldAuditTransfer() {
        UUID transferCode = UUID.randomUUID();

        listener.handleTransactionTransfer(
                new TransactionTransferEvent(
                        TransactionType.TRANSFER_SENT,
                        ACCOUNT_NUMBER,
                        "654321",
                        AMOUNT,
                        USERNAME,
                        transferCode
                )
        );

        verify(auditService).recordSuccess(
                AuditEventType.TRANSFER,
                AuditEntityType.TRANSFER,
                transferCode.toString()
        );
        verifyNoMoreInteractions(auditService);
    }
}
