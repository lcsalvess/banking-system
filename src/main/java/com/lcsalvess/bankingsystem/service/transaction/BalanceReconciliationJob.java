package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.projection.BalanceMismatch;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Confere se o saldo de cada conta bate com o balance_after da sua última
 * transação. Só detecta e registra: não corrige saldo automaticamente.
 */
@Component
public class BalanceReconciliationJob {

    private static final Logger log =
            LoggerFactory.getLogger(BalanceReconciliationJob.class);

    private final TransactionRepository transactionRepository;

    public BalanceReconciliationJob(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(
            cron = "${banking.reconciliation.cron}",
            zone = AppTimeZone.ID
    )
    public void run() {
        reconcile();
    }

    /**
     * @return quantidade de contas divergentes encontradas.
     */
    public int reconcile() {
        List<BalanceMismatch> mismatches;

        try {
            mismatches = transactionRepository.findBalanceMismatches();
        } catch (RuntimeException ex) {
            log.error("Balance reconciliation could not be executed.", ex);
            return 0;
        }

        if (mismatches.isEmpty()) {
            log.info("Balance reconciliation completed: no mismatches found.");
            return 0;
        }

        for (BalanceMismatch mismatch : mismatches) {
            Long firstBreak = findFirstBreakSafely(mismatch.getAccountId());

            log.error(
                    "Balance mismatch: accountId={}, accountNumber={}, "
                            + "accountBalance={}, ledgerBalance={}, "
                            + "firstChainBreakTransactionId={}",
                    mismatch.getAccountId(),
                    mismatch.getAccountNumber(),
                    mismatch.getAccountBalance(),
                    mismatch.getLedgerBalance(),
                    firstBreak
            );
        }

        log.error(
                "Balance reconciliation completed with {} mismatched account(s).",
                mismatches.size()
        );

        return mismatches.size();
    }

    private Long findFirstBreakSafely(Long accountId) {
        try {
            return transactionRepository.findFirstBalanceChainBreak(accountId)
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Could not locate the first chain break for account {}.",
                    accountId, ex);
            return null;
        }
    }
}