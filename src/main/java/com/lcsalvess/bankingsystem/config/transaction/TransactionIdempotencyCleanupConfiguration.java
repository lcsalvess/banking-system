package com.lcsalvess.bankingsystem.config.transaction;

import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyCleanupJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the scheduled cleanup job for transaction idempotency records.
 *
 * <p>Provides the repository dependency and retention period required
 * to remove expired idempotency records.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionIdempotencyCleanupConfiguration {

  /**
   * Creates the transaction idempotency cleanup job.
   *
   * <p>Uses the configured retention period, defaulting to seven days.
   *
   * @param idempotencyRepository the repository used to delete expired records
   * @param retentionDays the number of days records are retained
   * @return the configured transaction idempotency cleanup job
   */
  @Bean
  public TransactionIdempotencyCleanupJob transactionIdempotencyCleanupJob(
      TransactionIdempotencyRepository idempotencyRepository,
      @Value("${banking.idempotency.retention-days:7}") int retentionDays
  ) {
    return TransactionIdempotencyCleanupJob.create(
        idempotencyRepository,
        retentionDays
    );
  }
}
