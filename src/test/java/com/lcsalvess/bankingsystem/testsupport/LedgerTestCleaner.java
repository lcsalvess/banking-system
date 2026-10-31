package com.lcsalvess.bankingsystem.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Remove linhas de {@code transactions} nos testes de integração.
 *
 * <p>A tabela é append-only por trigger (V27), inclusive para o dono do
 * schema. A limpeza dos testes precisa dizer isso explicitamente: o trigger
 * é desabilitado, as linhas são removidas e o trigger é reabilitado na mesma
 * transação. Se qualquer passo falhar, o rollback desfaz também o DISABLE.
 *
 * <p>Só existe no classpath de teste, e só testes devem usá-lo.
 */
@Component
public class LedgerTestCleaner {

    private static final String UPDATE_DELETE_TRIGGER =
            "trg_transactions_no_update_delete";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public LedgerTestCleaner(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void deleteAllTransactions() {
        deleteTransactions("DELETE FROM transactions");
    }

    public void deleteTransactionsOfAccount(Long accountId) {
        deleteTransactions(
                "DELETE FROM transactions WHERE account_id = ?",
                accountId
        );
    }

    /**
     * Executa um UPDATE em {@code transactions} com o trigger desabilitado.
     * Só para testes que corrompem o ledger de propósito (reconciliação).
     */
    public void updateTransactions(String sql, Object... args) {
        runWithoutGuard(sql, args);
    }

    private void deleteTransactions(String sql, Object... args) {
        runWithoutGuard(sql, args);
    }

    private void runWithoutGuard(String sql, Object... args) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute(
                    "ALTER TABLE transactions DISABLE TRIGGER "
                            + UPDATE_DELETE_TRIGGER
            );
            jdbcTemplate.update(sql, args);
            jdbcTemplate.execute(
                    "ALTER TABLE transactions ENABLE TRIGGER "
                            + UPDATE_DELETE_TRIGGER
            );
        });
    }
}
