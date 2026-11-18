package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically removes expired transaction idempotency records.
 *
 * <p>Uses a configurable retention period to prevent the idempotency
 * table from accumulating outdated records.
 */
public class TransactionIdempotencyCleanupJob {

  private static final Logger log =
      LoggerFactory.getLogger(TransactionIdempotencyCleanupJob.class);

  private final TransactionIdempotencyRepository idempotencyRepository;
  private final int retentionDays;

  /**
   * Creates the cleanup job after validating the retention period.
   *
   * @param idempotencyRepository the repository used to delete expired records
   * @param retentionDays the number of days records are retained
   * @return the configured transaction idempotency cleanup job
   * @throws IllegalArgumentException if the retention period is less than one day
   */
  public static TransactionIdempotencyCleanupJob create(
      TransactionIdempotencyRepository idempotencyRepository,
      int retentionDays
  ) {
    if (retentionDays < 1) {
      throw new IllegalArgumentException(
          "banking.idempotency.retention-days must be greater than or equal to 1."
      );
    }

    return new TransactionIdempotencyCleanupJob(
        idempotencyRepository,
        retentionDays
    );
  }

  /**
   * Private constructor that only assigns the provided values.
   */
  private TransactionIdempotencyCleanupJob(
      TransactionIdempotencyRepository idempotencyRepository,
      int retentionDays
  ) {
    this.idempotencyRepository = idempotencyRepository;
    this.retentionDays = retentionDays;
  }

  /**
   * Deletes idempotency records older than the configured retention period.
   *
   * <p>Logs the number of deleted records after the cleanup completes.
   */
  @Scheduled(
      cron = "${banking.idempotency.cleanup-cron:0 30 3 * * *}",
      zone = "America/Sao_Paulo"
  )
  public void cleanUp() {
    int deleted = idempotencyRepository.deleteOlderThanDays(retentionDays);

    log.info(
        "Idempotency cleanup finished: deleted={}, retentionDays={}",
        deleted,
        retentionDays
    );
  }
}
