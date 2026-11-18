package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Provides specialized query operations for audit events without exposing
 * unrestricted repository mutation methods.
 */
public interface AuditEventQueryRepository extends
    Repository<AuditEvent, Long>,
    JpaSpecificationExecutor<AuditEvent> {

  /**
   * Retrieves audit events whose identifiers belong to the supplied collection.
   *
   * @param ids collection of audit event identifiers
   * @return matching audit events
   */
  List<AuditEvent> findByIdIn(Collection<Long> ids);

  /**
   * Retrieves only the IDs matching the specification, in the requested
   * order, without loading complete AuditEvent entities or running a count query.
   *
   * <p>The limit is expected to be the configured snapshot maximum plus one,
   * allowing the caller to detect an oversized result with a bounded query.
   *
   * @param specification criteria used to filter audit events
   * @param sort ordering applied to the matching identifiers
   * @param limit maximum number of identifiers to retrieve
   * @return matching audit event identifiers in the requested order
   */
  default List<Long> findMatchingEventIds(
      Specification<AuditEvent> specification,
      Sort sort,
      int limit
  ) {
    return findBy(
        specification,
        query -> query
            .as(AuditEventIdProjection.class)
            .project("id")
            .sortBy(sort)
            .limit(limit)
            .all()
    ).stream()
        .map(AuditEventIdProjection::getId)
        .toList();
  }

  /**
   * Projection exposing only the identifier of an audit event.
   */
  interface AuditEventIdProjection {

    /**
     * Retrieves the identifier of the projected audit event.
     *
     * @return audit event identifier
     */
    Long getId();
  }
}
