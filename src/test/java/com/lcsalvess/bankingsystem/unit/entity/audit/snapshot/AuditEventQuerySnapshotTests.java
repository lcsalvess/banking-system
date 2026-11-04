package com.lcsalvess.bankingsystem.unit.entity.audit.snapshot;

import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshot;
import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshotItem;
import com.lcsalvess.bankingsystem.entity.audit.snapshot.AuditEventQuerySnapshotItemId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Audit event query snapshot entities")
class AuditEventQuerySnapshotTests {

    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final Long ACTOR_ID = 42L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-11-03T20:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(900);

    @Nested
    @DisplayName("AuditEventQuerySnapshot")
    class SnapshotTests {

        @Test
        @DisplayName("Should keep the supplied values")
        void shouldKeepValues() {
            AuditEventQuerySnapshot snapshot = create(
                    hash((byte) 1),
                    hash((byte) 2),
                    10
            );

            assertEquals(SNAPSHOT_ID, snapshot.getSnapshotId());
            assertEquals(ACTOR_ID, snapshot.getActorUserId());
            assertArrayEquals(hash((byte) 1), snapshot.getFilterHash());
            assertArrayEquals(hash((byte) 2), snapshot.getSortHash());
            assertEquals(10, snapshot.getTotalElements());
            assertEquals(CREATED_AT, snapshot.getCreatedAt());
            assertEquals(EXPIRES_AT, snapshot.getExpiresAt());
        }

        @Test
        @DisplayName("Should accept the limits of the total element count")
        void shouldAcceptTotalElementLimits() {
            assertEquals(
                    0,
                    create(hash((byte) 1), hash((byte) 2), 0)
                            .getTotalElements()
            );

            assertEquals(
                    AuditEventQuerySnapshot.MAX_TOTAL_ELEMENTS,
                    create(
                            hash((byte) 1),
                            hash((byte) 2),
                            AuditEventQuerySnapshot.MAX_TOTAL_ELEMENTS
                    ).getTotalElements()
            );
        }

        @Test
        @DisplayName("Should not expose or depend on the caller's hash arrays")
        void shouldCopyHashesDefensively() {
            byte[] filterHash = hash((byte) 1);
            byte[] sortHash = hash((byte) 2);

            AuditEventQuerySnapshot snapshot = create(
                    filterHash,
                    sortHash,
                    1
            );

            filterHash[0] = 99;
            sortHash[0] = 99;

            assertArrayEquals(hash((byte) 1), snapshot.getFilterHash());
            assertArrayEquals(hash((byte) 2), snapshot.getSortHash());

            byte[] returnedFilterHash = snapshot.getFilterHash();
            byte[] returnedSortHash = snapshot.getSortHash();

            returnedFilterHash[0] = 99;
            returnedSortHash[0] = 99;

            assertNotSame(returnedFilterHash, snapshot.getFilterHash());
            assertNotSame(returnedSortHash, snapshot.getSortHash());

            assertArrayEquals(hash((byte) 1), snapshot.getFilterHash());
            assertArrayEquals(hash((byte) 2), snapshot.getSortHash());
        }

        @Test
        @DisplayName("Should reject missing required values")
        void shouldRejectNulls() {
            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            null, ACTOR_ID, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, EXPIRES_AT
                    ));

            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, null, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, EXPIRES_AT
                    ));

            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, null, hash((byte) 2),
                            1, CREATED_AT, EXPIRES_AT
                    ));

            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), null,
                            1, CREATED_AT, EXPIRES_AT
                    ));

            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), hash((byte) 2),
                            1, null, EXPIRES_AT
                    ));

            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, null
                    ));
        }

        @Test
        @DisplayName("Should reject a non-positive actor ID")
        void shouldRejectNonPositiveActorId() {
            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, 0L, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, EXPIRES_AT
                    ));
        }

        @Test
        @DisplayName("Should reject hashes that are not 32 bytes long")
        void shouldRejectWrongHashLength() {
            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, new byte[31], hash((byte) 2),
                            1, CREATED_AT, EXPIRES_AT
                    ));

            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), new byte[33],
                            1, CREATED_AT, EXPIRES_AT
                    ));
        }

        @Test
        @DisplayName("Should reject a total outside the allowed range")
        void shouldRejectTotalOutsideRange() {
            assertThrows(IllegalArgumentException.class, () ->
                    create(hash((byte) 1), hash((byte) 2), -1));

            assertThrows(IllegalArgumentException.class, () ->
                    create(
                            hash((byte) 1),
                            hash((byte) 2),
                            AuditEventQuerySnapshot.MAX_TOTAL_ELEMENTS + 1
                    ));
        }

        @Test
        @DisplayName("Should reject an expiration that is not after the creation")
        void shouldRejectInvalidExpiration() {
            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, CREATED_AT
                    ));

            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshot.create(
                            SNAPSHOT_ID, ACTOR_ID, hash((byte) 1), hash((byte) 2),
                            1, CREATED_AT, CREATED_AT.minusSeconds(1)
                    ));
        }

        private AuditEventQuerySnapshot create(
                byte[] filterHash,
                byte[] sortHash,
                int totalElements
        ) {
            return AuditEventQuerySnapshot.create(
                    SNAPSHOT_ID,
                    ACTOR_ID,
                    filterHash,
                    sortHash,
                    totalElements,
                    CREATED_AT,
                    EXPIRES_AT
            );
        }
    }

    @Nested
    @DisplayName("AuditEventQuerySnapshotItem")
    class ItemTests {

        @Test
        @DisplayName("Should keep the position and the audit event ID")
        void shouldKeepValues() {
            AuditEventQuerySnapshotItem item =
                    AuditEventQuerySnapshotItem.create(SNAPSHOT_ID, 3, 77L);

            assertEquals(SNAPSHOT_ID, item.getId().getSnapshotId());
            assertEquals(3, item.getId().getPosition());
            assertEquals(77L, item.getAuditEventId());
        }

        @Test
        @DisplayName("Should reject a missing or non-positive audit event ID")
        void shouldRejectInvalidAuditEventId() {
            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshotItem.create(SNAPSHOT_ID, 0, null));

            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshotItem.create(SNAPSHOT_ID, 0, 0L));
        }

        @Test
        @DisplayName("Should reject a missing snapshot ID or negative position")
        void shouldRejectInvalidId() {
            assertThrows(NullPointerException.class, () ->
                    AuditEventQuerySnapshotItem.create(null, 0, 1L));

            assertThrows(IllegalArgumentException.class, () ->
                    AuditEventQuerySnapshotItem.create(SNAPSHOT_ID, -1, 1L));
        }
    }

    @Nested
    @DisplayName("AuditEventQuerySnapshotItemId")
    class ItemIdTests {

        @Test
        @DisplayName("Should compare by snapshot ID and position")
        void shouldImplementEqualsAndHashCode() {
            AuditEventQuerySnapshotItemId id =
                    AuditEventQuerySnapshotItemId.create(SNAPSHOT_ID, 1);

            AuditEventQuerySnapshotItemId same =
                    AuditEventQuerySnapshotItemId.create(SNAPSHOT_ID, 1);

            assertEquals(id, same);
            assertEquals(id.hashCode(), same.hashCode());

            assertNotEquals(
                    AuditEventQuerySnapshotItemId.create(SNAPSHOT_ID, 2),
                    id
            );

            assertNotEquals(
                    AuditEventQuerySnapshotItemId.create(UUID.randomUUID(), 1),
                    id
            );

            assertNotEquals(null, id);
        }
    }

    private static byte[] hash(byte fill) {
        byte[] hash = new byte[AuditEventQuerySnapshot.HASH_LENGTH];
        Arrays.fill(hash, fill);
        return hash;
    }
}
