package com.lcsalvess.bankingsystem.repository.idempotency;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransactionIdempotencyRepository {

    private static final String INSERT_SQL = """
            INSERT INTO transaction_idempotency_requests (
                user_id,
                idempotency_key,
                operation_type,
                request_hash
            )
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """;

    private static final String FIND_SQL = """
            SELECT
                idempotency_key,
                operation_type,
                request_hash,
                transaction_code
            FROM transaction_idempotency_requests
            WHERE user_id = ?
              AND idempotency_key = ?
            """;

    private static final String COMPLETE_SQL = """
            UPDATE transaction_idempotency_requests
            SET transaction_code = ?
            WHERE user_id = ?
              AND idempotency_key = ?
              AND transaction_code IS NULL
            """;

    // Usa a mesma expressão do DEFAULT de created_at (horário local de
    // America/Sao_Paulo), então a comparação não depende do fuso da JVM.
    private static final String DELETE_OLDER_THAN_SQL = """
            DELETE FROM transaction_idempotency_requests
            WHERE created_at < (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')
                               - make_interval(days => ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "JdbcTemplate is a Spring-managed collaborator intentionally retained for repository operations."
    )
    public TransactionIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Tenta reservar a chave. Retorna true se a reserva foi criada
     * e false se já existia uma reserva para (userId, idempotencyKey).
     */
    public boolean tryReserve(
            long userId,
            UUID idempotencyKey,
            String operationType,
            String requestHash
    ) {
        // Tipos informados explicitamente para o driver PostgreSQL tratar UUIDs corretamente.
        int insertedRows = jdbcTemplate.update(
                INSERT_SQL,
                ps -> {
                    ps.setLong(1, userId);
                    ps.setObject(2, idempotencyKey, Types.OTHER);
                    ps.setString(3, operationType);
                    ps.setString(4, requestHash);
                }
        );

        return insertedRows == 1;
    }

    public Optional<TransactionIdempotencyRecord> findByUserIdAndKey(
            long userId,
            UUID idempotencyKey
    ) {
        return jdbcTemplate.query(
                FIND_SQL,
                ps -> {
                    ps.setLong(1, userId);
                    ps.setObject(2, idempotencyKey, Types.OTHER);
                },
                (rs, rowNum) -> new TransactionIdempotencyRecord(
                        rs.getObject("idempotency_key", UUID.class),
                        rs.getString("operation_type"),
                        rs.getString("request_hash"),
                        rs.getObject("transaction_code", UUID.class)
                )
        ).stream().findFirst();
    }

    public void complete(
            long userId,
            UUID idempotencyKey,
            UUID transactionCode
    ) {
        int updatedRows = jdbcTemplate.update(
                COMPLETE_SQL,
                ps -> {
                    ps.setObject(1, transactionCode, Types.OTHER);
                    ps.setLong(2, userId);
                    ps.setObject(3, idempotencyKey, Types.OTHER);
                }
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Não foi possível concluir o registro de idempotência. O registro pode não existir ou já foi finalizado."
            );
        }
    }

    /**
     * Remove os registros criados há mais de {@code retentionDays} dias.
     * Retorna a quantidade de linhas removidas.
     */
    public int deleteOlderThanDays(int retentionDays) {
        return jdbcTemplate.update(DELETE_OLDER_THAN_SQL, retentionDays);
    }
}