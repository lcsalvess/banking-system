package com.lcsalvess.bankingsystem.config.transaction;

import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionIdempotencyCleanupJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TransactionIdempotencyCleanupConfiguration {

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