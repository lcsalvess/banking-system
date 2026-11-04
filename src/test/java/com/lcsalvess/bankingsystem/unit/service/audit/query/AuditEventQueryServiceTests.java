package com.lcsalvess.bankingsystem.unit.service.audit.query;

import com.lcsalvess.bankingsystem.dto.response.audit.AuditEventResponseDTO;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventPageResult;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.query.AuditEventQueryService;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventQueryService tests")
class AuditEventQueryServiceTests {

    private static final UUID SNAPSHOT_ID = UUID.randomUUID();

    @Mock
    private AuditEventQuerySnapshotService snapshotService;

    private AuditEventQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new AuditEventQueryService(snapshotService);
    }

    @Nested
    @DisplayName("Snapshot creation")
    class SnapshotCreationTests {

        @Test
        @DisplayName("Should create a snapshot when no token is supplied")
        void shouldCreateSnapshotWhenTokenIsAbsent() {
            AuditEventQueryFilter filter = filters();

            Pageable pageable = PageRequest.of(
                    0,
                    20,
                    defaultSort()
            );

            AuditEventPageResult expectedResult = result();

            when(snapshotService.createSnapshot(
                    filter,
                    pageable
            )).thenReturn(expectedResult);

            AuditEventPageResult actualResult = execute(
                    filter,
                    pageable,
                    null
            );

            assertSame(expectedResult, actualResult);

            verify(snapshotService).createSnapshot(
                    filter,
                    pageable
            );

            verifyNoMoreInteractions(snapshotService);
        }
    }

    @Nested
    @DisplayName("Existing snapshot navigation")
    class ExistingSnapshotTests {

        @Test
        @DisplayName("Should retrieve a page when a snapshot token is supplied")
        void shouldRetrievePageFromExistingSnapshot() {
            AuditEventQueryFilter filter = filters();

            Pageable pageable = PageRequest.of(
                    2,
                    10,
                    Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("id")
                    )
            );

            AuditEventPageResult expectedResult = result();
            String snapshotToken = SNAPSHOT_ID.toString();

            when(snapshotService.findPage(
                    filter,
                    pageable,
                    snapshotToken
            )).thenReturn(expectedResult);

            AuditEventPageResult actualResult = execute(
                    filter,
                    pageable,
                    snapshotToken
            );

            assertSame(expectedResult, actualResult);

            verify(snapshotService).findPage(
                    filter,
                    pageable,
                    snapshotToken
            );

            verifyNoMoreInteractions(snapshotService);
        }

        @Test
        @DisplayName("Should delegate a blank token to snapshot validation")
        void shouldDelegateBlankTokenToSnapshotValidation() {
            AuditEventQueryFilter filter = emptyFilter();

            Pageable pageable = PageRequest.of(
                    0,
                    20,
                    defaultSort()
            );

            String snapshotToken = " ";

            AuditEventPageResult expectedResult = result();

            when(snapshotService.findPage(
                    filter,
                    pageable,
                    snapshotToken
            )).thenReturn(expectedResult);

            AuditEventPageResult actualResult = execute(
                    filter,
                    pageable,
                    snapshotToken
            );

            assertSame(expectedResult, actualResult);

            verify(snapshotService).findPage(
                    filter,
                    pageable,
                    snapshotToken
            );

            verifyNoMoreInteractions(snapshotService);
        }
    }

    private AuditEventPageResult execute(
            AuditEventQueryFilter filter,
            Pageable pageable,
            String snapshotToken
    ) {
        return queryService.findAll(
                filter,
                pageable,
                snapshotToken
        );
    }

    private static AuditEventPageResult result() {
        Page<AuditEventResponseDTO> page = new PageImpl<>(List.of());

        return new AuditEventPageResult(
                page,
                SNAPSHOT_ID.toString()
        );
    }

    private static Sort defaultSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }

    private static AuditEventQueryFilter filters() {
        return new AuditEventQueryFilter(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 25),
                com.lcsalvess.bankingsystem.entity.audit.AuditEventType.TRANSFER,
                com.lcsalvess.bankingsystem.entity.audit.AuditOutcome.SUCCESS,
                42L,
                "lucas",
                com.lcsalvess.bankingsystem.entity.audit.AuditEntityType.ACCOUNT,
                "12345"
        );
    }

    private static AuditEventQueryFilter emptyFilter() {
        return new AuditEventQueryFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
