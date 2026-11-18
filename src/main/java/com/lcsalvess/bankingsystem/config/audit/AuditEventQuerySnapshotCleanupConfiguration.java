package com.lcsalvess.bankingsystem.config.audit;

import com.lcsalvess.bankingsystem.repository.audit.AuditEventQuerySnapshotRepository;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotCleanupJob;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the cleanup job for expired audit event query snapshots.
 */
@Configuration(proxyBeanMethods = false)
public class AuditEventQuerySnapshotCleanupConfiguration {

  /**
   * Registers the job responsible for removing expired query snapshots.
   *
   * @param snapshotRepository the repository used to access stored snapshots
   * @param clock the clock used to determine snapshot expiration
   * @return the configured snapshot cleanup job
   */
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
