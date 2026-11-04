package com.lcsalvess.bankingsystem.unit.service.audit.recording;

import com.lcsalvess.bankingsystem.entity.audit.AuditActorType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEntityType;
import com.lcsalvess.bankingsystem.entity.audit.AuditEventType;
import com.lcsalvess.bankingsystem.entity.audit.AuditOutcome;
import com.lcsalvess.bankingsystem.service.audit.recording.DetachedAuditRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("DetachedAuditRecord")
class DetachedAuditRecordTests {

    private static final InetAddress IPV4 = address(
            (byte) 192, (byte) 0, (byte) 2, (byte) 10
    );

    private static final InetAddress IPV6 = address(
            (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01
    );

    private static InetAddress address(byte... bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static DetachedAuditRecord record(
            AuditOutcome outcome,
            String username,
            String entityId,
            String reason,
            InetAddress ipAddress,
            Integer status
    ) {
        return new DetachedAuditRecord(
                AuditEventType.LOGIN_FAILURE,
                outcome,
                AuditActorType.ANONYMOUS,
                null,
                username,
                AuditEntityType.AUTHENTICATION,
                entityId,
                reason,
                ipAddress,
                status,
                null
        );
    }

    @Test
    @DisplayName("Should keep valid values unchanged")
    void shouldKeepValidValues() {
        DetachedAuditRecord record = record(
                AuditOutcome.FAILURE,
                "lucas",
                "lucas",
                "INVALID_CREDENTIALS",
                IPV4,
                401
        );

        assertEquals("lucas", record.actorUsername());
        assertEquals("lucas", record.entityId());
        assertEquals("INVALID_CREDENTIALS", record.reason());
        assertEquals(IPV4, record.ipAddress());
        assertEquals(401, record.httpStatus());
    }

    @Test
    @DisplayName("Should truncate username and entity id to the column sizes")
    void shouldTruncateToColumnSizes() {
        DetachedAuditRecord record = record(
                AuditOutcome.FAILURE,
                "u".repeat(50),
                "e".repeat(200),
                "INVALID_CREDENTIALS",
                null,
                null
        );

        assertEquals(20, record.actorUsername().length());
        assertEquals(64, record.entityId().length());
    }

    @ParameterizedTest(name = "[{index}] entityId = \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should replace a blank entity id")
    void shouldReplaceBlankEntityId(String entityId) {
        assertEquals(
                "UNKNOWN",
                record(AuditOutcome.FAILURE, null, entityId, "X", null, null)
                        .entityId()
        );
    }

    @Test
    @DisplayName("Should replace a null entity id")
    void shouldReplaceNullEntityId() {
        assertEquals(
                "UNKNOWN",
                record(AuditOutcome.FAILURE, null, null, "X", null, null)
                        .entityId()
        );
    }

    @Test
    @DisplayName("Should preserve an IPv6 address")
    void shouldAcceptIpv6() {
        assertEquals(
                IPV6,
                record(
                        AuditOutcome.FAILURE,
                        null,
                        "x",
                        "X",
                        IPV6,
                        null
                ).ipAddress()
        );
    }

    @Test
    @DisplayName("Should preserve a null IP address")
    void shouldPreserveNullIpAddress() {
        assertNull(
                record(AuditOutcome.FAILURE, null, "x", "X", null, null)
                        .ipAddress()
        );
    }

    @ParameterizedTest(name = "[{index}] reason = \"{0}\"")
    @ValueSource(strings = {
            "lowercase",
            "WITH SPACE",
            "1STARTS_WITH_DIGIT",
            ""
    })
    @DisplayName("Should replace an invalid reason on non-success events")
    void shouldReplaceInvalidReasonOnFailure(String reason) {
        assertEquals(
                "UNSPECIFIED",
                record(AuditOutcome.FAILURE, null, "x", reason, null, null)
                        .reason()
        );
    }

    @Test
    @DisplayName("Should replace a null reason on non-success events")
    void shouldReplaceNullReasonOnFailure() {
        assertEquals(
                "UNSPECIFIED",
                record(AuditOutcome.DENIED, null, "x", null, null, null)
                        .reason()
        );
    }

    @Test
    @DisplayName("Should leave the reason empty on success events")
    void shouldLeaveReasonEmptyOnSuccess() {
        assertNull(
                record(AuditOutcome.SUCCESS, "lucas", "lucas", null, null, null)
                        .reason()
        );
    }

    @ParameterizedTest(name = "[{index}] status = {0}")
    @ValueSource(ints = {0, 99, 600, -1})
    @DisplayName("Should drop an HTTP status outside 100-599")
    void shouldDropInvalidStatus(int status) {
        assertNull(
                record(AuditOutcome.FAILURE, null, "x", "X", null, status)
                        .httpStatus()
        );
    }

    @Test
    @DisplayName("Should require event type, outcome, actor type and entity type")
    void shouldRequireMandatoryFields() {
        assertThrows(
                NullPointerException.class,
                () -> new DetachedAuditRecord(
                        null,
                        AuditOutcome.FAILURE,
                        AuditActorType.ANONYMOUS,
                        null, null,
                        AuditEntityType.AUTHENTICATION,
                        "x", "X", null, null, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new DetachedAuditRecord(
                        AuditEventType.LOGIN_FAILURE,
                        null,
                        AuditActorType.ANONYMOUS,
                        null, null,
                        AuditEntityType.AUTHENTICATION,
                        "x", "X", null, null, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new DetachedAuditRecord(
                        AuditEventType.LOGIN_FAILURE,
                        AuditOutcome.FAILURE,
                        null,
                        null, null,
                        AuditEntityType.AUTHENTICATION,
                        "x", "X", null, null, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new DetachedAuditRecord(
                        AuditEventType.LOGIN_FAILURE,
                        AuditOutcome.FAILURE,
                        AuditActorType.ANONYMOUS,
                        null, null,
                        null,
                        "x", "X", null, null, null
                )
        );
    }
}