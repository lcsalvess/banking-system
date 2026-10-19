package com.lcsalvess.bankingsystem.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionIdempotencyCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionIdempotencyCleanupJob.class);

    private final TransactionIdempotencyRepository idempotencyRepository;
    private final int retentionDays;

    public TransactionIdempotencyCleanupJob(
            TransactionIdempotencyRepository idempotencyRepository,
            @Value("${banking.idempotency.retention-days:7}") int retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException(
                    "banking.idempotency.retention-days deve ser maior ou igual a 1."
            );
        }

        this.idempotencyRepository = idempotencyRepository;
        this.retentionDays = retentionDays;
    }

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