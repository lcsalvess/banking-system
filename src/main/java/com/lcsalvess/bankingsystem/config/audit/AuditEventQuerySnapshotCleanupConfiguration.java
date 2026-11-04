package com.lcsalvess.bankingsystem.config.audit;

import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotCleanupJob;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AuditEventQuerySnapshotCleanupConfiguration {

    @Bean
    public AuditEventQuerySnapshotCleanupJob auditEventQuerySnapshotCleanupJob(
            AuditEventQuerySnapshotRepository snapshotRepository,
            Clock clock
    ) {
        return AuditEventQuerySnapshotCleanupJob.create(
                snapshotRepository,
                clock
        );
    }
}
