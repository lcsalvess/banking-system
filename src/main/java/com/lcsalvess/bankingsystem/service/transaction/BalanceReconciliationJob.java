package com.lcsalvess.bankingsystem.service.transaction;

import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.projection.BalanceChainBreak;
import com.lcsalvess.bankingsystem.repository.projection.BalanceMismatch;
import com.lcsalvess.bankingsystem.util.time.AppTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Verifica a consistência dos saldos das contas e da cadeia histórica
 * de snapshots das transações.
 * <p>
 * Detecta divergências entre o saldo atual e o último {@code balance_after},
 * bem como quebras na sequência histórica dos saldos. Apenas registra as
 * inconsistências encontradas: não corrige saldos automaticamente.
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
     * @return quantidade de contas com divergências de saldo ou histórico.
     */
    public int reconcile() {
        List<BalanceMismatch> mismatches;

        try {
            mismatches = transactionRepository.findBalanceMismatches();
        } catch (RuntimeException ex) {
            log.error("Balance reconciliation could not be executed.", ex);
            return 0;
        }

        List<BalanceChainBreak> chainBreaks;

        try {
            chainBreaks = transactionRepository.findBalanceChainBreaks();
        } catch (RuntimeException ex) {
            log.error("Balance chain reconciliation could not be executed.", ex);
            return 0;
        }

        Map<Long, BalanceMismatch> mismatchesByAccount = new TreeMap<>();
        for (BalanceMismatch mismatch : mismatches) {
            mismatchesByAccount.put(mismatch.getAccountId(), mismatch);
        }

        Map<Long, Long> firstBreakByAccount = new TreeMap<>();
        for (BalanceChainBreak chainBreak : chainBreaks) {
            firstBreakByAccount.put(
                    chainBreak.getAccountId(),
                    chainBreak.getTransactionId()
            );
        }

        if (mismatchesByAccount.isEmpty() && firstBreakByAccount.isEmpty()) {
            log.info("Balance reconciliation completed: no mismatches found.");
            return 0;
        }

        Set<Long> affectedAccounts = new TreeSet<>();
        affectedAccounts.addAll(mismatchesByAccount.keySet());
        affectedAccounts.addAll(firstBreakByAccount.keySet());

        for (Long accountId : affectedAccounts) {
            BalanceMismatch mismatch = mismatchesByAccount.get(accountId);
            Long firstBreak = firstBreakByAccount.get(accountId);

            if (mismatch != null) {
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
            } else {
                log.error(
                        "Balance chain break: accountId={}, "
                                + "firstChainBreakTransactionId={}",
                        accountId,
                        firstBreak
                );
            }
        }

        log.error(
                "Balance reconciliation completed with {} affected account(s).",
                affectedAccounts.size()
        );

        return affectedAccounts.size();
    }
}
