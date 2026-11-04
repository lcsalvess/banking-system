package com.lcsalvess.bankingsystem.unit.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.service.audit.query.snapshot.AuditEventQuerySnapshotFingerprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("AuditEventQuerySnapshotFingerprint tests")
class AuditEventQuerySnapshotFingerprintTests {

    private static final int SHA_256_LENGTH = 32;

    @Nested
    @DisplayName("filters")
    class FilterTests {

        @Test
        @DisplayName("Should produce a 32-byte deterministic digest")
        void shouldBeDeterministic() {
            byte[] first = AuditEventQuerySnapshotFingerprint.filters(
                    none()
            );

            byte[] second = AuditEventQuerySnapshotFingerprint.filters(
                    none()
            );

            assertEquals(SHA_256_LENGTH, first.length);
            assertArrayEquals(first, second);
        }

        @Test
        @DisplayName("Should ignore case and surrounding spaces in the username")
        void shouldNormalizeUsername() {
            assertArrayEquals(
                    filters(withUsername("lucas")),
                    filters(withUsername("  LuCaS "))
            );
        }

        @Test
        @DisplayName("Should ignore surrounding spaces in the entity ID but keep its case")
        void shouldNormalizeEntityIdWithoutChangingCase() {
            assertArrayEquals(
                    filters(withEntityId("ABC")),
                    filters(withEntityId(" ABC "))
            );

            assertFalse(Arrays.equals(
                    filters(withEntityId("ABC")),
                    filters(withEntityId("abc"))
            ));
        }

        @Test
        @DisplayName("Should distinguish an absent filter from an empty-looking value")
        void shouldDistinguishNullFromValue() {
            assertFalse(Arrays.equals(
                    filters(none()),
                    filters(withEntityId("0"))
            ));
        }

        @Test
        @DisplayName("Should change when any filter component changes")
        void shouldChangeWhenAnyComponentChanges() {
            AuditEventQueryFilter base = full();

            AuditEventQueryFilter[] variants = {
                    new AuditEventQueryFilter(
                            base.startDate().plusDays(1), base.endDate(),
                            base.eventType(), base.outcome(),
                            base.actorUserId(), base.actorUsername(),
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate().plusDays(1),
                            base.eventType(), base.outcome(),
                            base.actorUserId(), base.actorUsername(),
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            AuditEventType.USER_CREATED, base.outcome(),
                            base.actorUserId(), base.actorUsername(),
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            base.eventType(), AuditOutcome.FAILURE,
                            base.actorUserId(), base.actorUsername(),
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            base.eventType(), base.outcome(),
                            43L, base.actorUsername(),
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            base.eventType(), base.outcome(),
                            base.actorUserId(), "other",
                            base.entityType(), base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            base.eventType(), base.outcome(),
                            base.actorUserId(), base.actorUsername(),
                            AuditEntityType.USER, base.entityId()),
                    new AuditEventQueryFilter(
                            base.startDate(), base.endDate(),
                            base.eventType(), base.outcome(),
                            base.actorUserId(), base.actorUsername(),
                            base.entityType(), "99999")
            };

            byte[] baseline = filters(base);

            for (AuditEventQueryFilter variant : variants) {
                assertFalse(
                        Arrays.equals(baseline, filters(variant)),
                        "The digest must change for " + variant
                );
            }
        }

        private AuditEventQueryFilter none() {
            return new AuditEventQueryFilter(
                    null, null, null, null,
                    null, null, null, null
            );
        }

        private byte[] filters(AuditEventQueryFilter filter) {
            return AuditEventQuerySnapshotFingerprint.filters(filter);
        }

        private AuditEventQueryFilter withUsername(String username) {
            return new AuditEventQueryFilter(
                    null, null, null, null,
                    null, username, null, null
            );
        }

        private AuditEventQueryFilter withEntityId(String entityId) {
            return new AuditEventQueryFilter(
                    null, null, null, null,
                    null, null, null, entityId
            );
        }

        private AuditEventQueryFilter full() {
            return new AuditEventQueryFilter(
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 25),
                    AuditEventType.TRANSFER,
                    AuditOutcome.SUCCESS,
                    42L,
                    "lucas",
                    AuditEntityType.ACCOUNT,
                    "12345"
            );
        }
    }

    @Nested
    @DisplayName("sort")
    class SortTests {

        @Test
        @DisplayName("Should produce a 32-byte deterministic digest")
        void shouldBeDeterministic() {
            Sort sort = Sort.by(Sort.Order.desc("createdAt"));

            byte[] first = AuditEventQuerySnapshotFingerprint.sort(sort);
            byte[] second = AuditEventQuerySnapshotFingerprint.sort(sort);

            assertEquals(SHA_256_LENGTH, first.length);
            assertArrayEquals(first, second);
        }

        @Test
        @DisplayName("Should depend on property, direction and order of the orders")
        void shouldDependOnPropertyDirectionAndPosition() {
            byte[] baseline = AuditEventQuerySnapshotFingerprint.sort(
                    Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.asc("id")
                    )
            );

            assertFalse(Arrays.equals(
                    baseline,
                    AuditEventQuerySnapshotFingerprint.sort(
                            Sort.by(
                                    Sort.Order.asc("createdAt"),
                                    Sort.Order.asc("id")
                            )
                    )
            ));

            assertFalse(Arrays.equals(
                    baseline,
                    AuditEventQuerySnapshotFingerprint.sort(
                            Sort.by(
                                    Sort.Order.asc("id"),
                                    Sort.Order.desc("createdAt")
                            )
                    )
            ));

            assertFalse(Arrays.equals(
                    baseline,
                    AuditEventQuerySnapshotFingerprint.sort(
                            Sort.by(
                                    Sort.Order.desc("eventType"),
                                    Sort.Order.asc("id")
                            )
                    )
            ));
        }

        @Test
        @DisplayName("Should distinguish ignore-case and null handling")
        void shouldDistinguishIgnoreCaseAndNullHandling() {
            byte[] plain = AuditEventQuerySnapshotFingerprint.sort(
                    Sort.by(Sort.Order.asc("actorUsername"))
            );

            assertFalse(Arrays.equals(
                    plain,
                    AuditEventQuerySnapshotFingerprint.sort(
                            Sort.by(Sort.Order.asc("actorUsername")
                                    .ignoreCase())
                    )
            ));

            assertFalse(Arrays.equals(
                    plain,
                    AuditEventQuerySnapshotFingerprint.sort(
                            Sort.by(Sort.Order.asc("actorUsername")
                                    .nullsFirst())
                    )
            ));
        }
    }
}
