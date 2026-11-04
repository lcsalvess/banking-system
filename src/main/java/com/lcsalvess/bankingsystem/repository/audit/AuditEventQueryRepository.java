package com.lcsalvess.bankingsystem.repository.audit;

import com.lcsalvess.bankingsystem.entity.audit.AuditEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

public interface AuditEventQueryRepository extends
        Repository<AuditEvent, Long>,
        JpaSpecificationExecutor<AuditEvent> {

    List<AuditEvent> findByIdIn(Collection<Long> ids);

    /**
     * Retrieves only the IDs matching the specification, in the requested
     * order, without loading complete AuditEvent entities or running a count query.
     *
     * <p>The limit is expected to be the configured snapshot maximum plus one,
     * allowing the caller to detect an oversized result with a bounded query.
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

    interface AuditEventIdProjection {

        Long getId();
    }
}
