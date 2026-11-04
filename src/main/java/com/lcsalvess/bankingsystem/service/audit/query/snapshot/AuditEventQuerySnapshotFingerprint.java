package com.lcsalvess.bankingsystem.service.audit.query.snapshot;

import com.lcsalvess.bankingsystem.service.audit.contract.AuditEventQueryFilter;
import com.lcsalvess.bankingsystem.util.normalization.OptionalTextNormalizer;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuditEventQuerySnapshotFingerprint {

    private AuditEventQuerySnapshotFingerprint() {
    }

    public static byte[] filters(AuditEventQueryFilter filter) {
        String normalizedUsername =
                OptionalTextNormalizer.normalize(filter.actorUsername());

        String fingerprintUsername = normalizedUsername == null
                ? null
                : normalizedUsername.toLowerCase(Locale.ROOT);

        String normalizedEntityId =
                OptionalTextNormalizer.normalize(filter.entityId());

        return digest(output -> {
            writeNullableString(
                    output,
                    filter.startDate() == null
                            ? null
                            : filter.startDate().toString()
            );

            writeNullableString(
                    output,
                    filter.endDate() == null
                            ? null
                            : filter.endDate().toString()
            );

            writeNullableString(
                    output,
                    filter.eventType() == null
                            ? null
                            : filter.eventType().name()
            );

            writeNullableString(
                    output,
                    filter.outcome() == null
                            ? null
                            : filter.outcome().name()
            );

            writeNullableString(
                    output,
                    filter.actorUserId() == null
                            ? null
                            : filter.actorUserId().toString()
            );

            writeNullableString(output, fingerprintUsername);

            writeNullableString(
                    output,
                    filter.entityType() == null
                            ? null
                            : filter.entityType().name()
            );

            writeNullableString(output, normalizedEntityId);
        });
    }

    public static byte[] sort(Sort sort) {
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);

        return digest(output -> {
            output.writeInt(orders.size());

            for (Sort.Order order : orders) {
                writeNullableString(output, order.getProperty());
                writeNullableString(output, order.getDirection().name());
                writeNullableString(output, order.getNullHandling().name());
                output.writeBoolean(order.isIgnoreCase());
            }
        });
    }

    private static byte[] digest(Encoder encoder) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(buffer)) {
            encoder.write(output);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not encode audit query snapshot fingerprint.",
                    exception
            );
        }

        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(buffer.toByteArray());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }

    private static void writeNullableString(
            DataOutputStream output,
            String value
    ) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @FunctionalInterface
    private interface Encoder {

        void write(DataOutputStream output) throws IOException;
    }
}
