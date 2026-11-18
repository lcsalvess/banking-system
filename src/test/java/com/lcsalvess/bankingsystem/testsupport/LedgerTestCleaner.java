package com.lcsalvess.bankingsystem.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes rows from {@code transactions} in integration tests.
 *
 * <p>The table is append-only through a trigger (V27), including for the
 * schema owner. Test cleanup must explicitly bypass this protection:
 * the trigger is disabled, the rows are deleted, and the trigger is
 * re-enabled within the same transaction. If any step fails, the
 * rollback also undoes the DISABLE operation.
 *
 * <p>This component is only available on the test classpath and should
 * only be used by tests.
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
   * Executes an UPDATE on {@code transactions} with the trigger disabled.
   *
   * <p>Only intended for tests that deliberately corrupt the ledger,
   * such as balance reconciliation tests.
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
