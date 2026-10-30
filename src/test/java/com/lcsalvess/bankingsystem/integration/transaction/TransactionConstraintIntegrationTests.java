package com.lcsalvess.bankingsystem.integration.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@DisplayName("Transaction database constraints")
class TransactionConstraintIntegrationTests {

    private static final String AMOUNT_POSITIVE_CONSTRAINT =
            "ck_transactions_amount_positive";

    private static final String AMOUNT_MAX_CONSTRAINT =
            "ck_transactions_amount_max";

    private static final String TRANSFER_CODE_CONSTRAINT =
            "ck_transactions_transfer_code";

    private static final String TRANSFER_CODE_UNIQUE_INDEX =
            "uk_transactions_transfer_code_type";

    private static final String ACTOR_CONSTRAINT =
            "ck_transactions_actor";

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long checkingAccountId;
    private Long destinationAccountId;
    private Long savingsAccountId;

    private int clientSequence;

    @BeforeEach
    void setUp() {
        clientSequence = 0;

        userId = createUser();

        checkingAccountId = createAccount(
                "CHECKING",
                "91001"
        );

        destinationAccountId = createAccount(
                "CHECKING",
                "91002"
        );

        savingsAccountId = createAccount(
                "SAVINGS",
                "91003"
        );
    }

    // ============================================================
    // Amount constraints
    // ============================================================

    @Test
    @DisplayName("Should accept the maximum supported transaction amount")
    void shouldAcceptMaximumTransactionAmount() {
        assertDoesNotThrow(() ->
                insertTransaction(
                        "DEPOSIT",
                        MAX_AMOUNT,
                        checkingAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    @Test
    @DisplayName("Should reject transaction amounts above the maximum")
    void shouldRejectAmountAboveMaximum() {
        assertConstraintViolation(
                AMOUNT_MAX_CONSTRAINT,
                () -> insertTransaction(
                        "DEPOSIT",
                        new BigDecimal("10000000000000.00"),
                        checkingAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    @ParameterizedTest(name = "Should reject amount {0}")
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should reject non-positive transaction amounts")
    void shouldRejectNonPositiveAmounts(String amount) {
        assertConstraintViolation(
                AMOUNT_POSITIVE_CONSTRAINT,
                () -> insertTransaction(
                        "DEPOSIT",
                        new BigDecimal(amount),
                        checkingAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    // ============================================================
    // Transfer code constraints
    // ============================================================

    @ParameterizedTest(name = "Should require a transfer code for {0}")
    @ValueSource(strings = {
            "TRANSFER_SENT",
            "TRANSFER_RECEIVED"
    })
    @DisplayName("Should reject transfer transactions without a transfer code")
    void shouldRejectTransferWithoutCode(String type) {
        assertConstraintViolation(
                TRANSFER_CODE_CONSTRAINT,
                () -> insertTransaction(
                        type,
                        new BigDecimal("100.00"),
                        checkingAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    @ParameterizedTest(name = "Should prohibit transfer codes on {0}")
    @ValueSource(strings = {
            "DEPOSIT",
            "WITHDRAWAL"
    })
    @DisplayName("Should reject non-transfer transactions with a transfer code")
    void shouldRejectNonTransferWithCode(String type) {
        assertConstraintViolation(
                TRANSFER_CODE_CONSTRAINT,
                () -> insertTransaction(
                        type,
                        new BigDecimal("100.00"),
                        checkingAccountId,
                        UUID.randomUUID(),
                        userId,
                        false
                )
        );
    }

    @ParameterizedTest(name = "Should prevent duplicate {0} transactions")
    @ValueSource(strings = {
            "TRANSFER_SENT",
            "TRANSFER_RECEIVED"
    })
    @DisplayName("Should reject duplicate transfer directions for the same code")
    void shouldRejectDuplicateTransferDirection(String type) {
        UUID transferCode = UUID.randomUUID();

        insertTransaction(
                type,
                new BigDecimal("100.00"),
                checkingAccountId,
                transferCode,
                userId,
                false
        );

        assertConstraintViolation(
                TRANSFER_CODE_UNIQUE_INDEX,
                () -> insertTransaction(
                        type,
                        new BigDecimal("100.00"),
                        destinationAccountId,
                        transferCode,
                        userId,
                        false
                )
        );
    }

    @Test
    @DisplayName("Should allow one sent and one received transaction with the same code")
    void shouldAllowTransferPairWithSameCode() {
        UUID transferCode = UUID.randomUUID();

        assertDoesNotThrow(() -> {
            insertTransaction(
                    "TRANSFER_SENT",
                    new BigDecimal("100.00"),
                    checkingAccountId,
                    transferCode,
                    userId,
                    false
            );

            insertTransaction(
                    "TRANSFER_RECEIVED",
                    new BigDecimal("100.00"),
                    destinationAccountId,
                    transferCode,
                    userId,
                    false
            );
        });
    }

    @Test
    @DisplayName("Should preserve the transfer code constraint as NOT VALID for legacy records")
    void shouldKeepTransferCodeConstraintNotValidated() {
        Boolean validated = jdbcTemplate.queryForObject(
                """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE conname = ?
                        """,
                Boolean.class,
                TRANSFER_CODE_CONSTRAINT
        );

        assertEquals(
                Boolean.FALSE,
                validated,
                "The transfer code constraint must remain NOT VALID "
                        + "to preserve legacy transfers without a code."
        );
    }

    // ============================================================
    // Transaction actor constraints
    // ============================================================

    @Test
    @DisplayName("Should accept a human-attributed transaction")
    void shouldAcceptHumanAttributedTransaction() {
        assertDoesNotThrow(() ->
                insertTransaction(
                        "DEPOSIT",
                        new BigDecimal("100.00"),
                        checkingAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    @Test
    @DisplayName("Should reject a non-yield transaction attributed to the system")
    void shouldRejectSystemAttributedNonYield() {
        assertConstraintViolation(
                ACTOR_CONSTRAINT,
                () -> insertTransaction(
                        "DEPOSIT",
                        new BigDecimal("100.00"),
                        checkingAccountId,
                        null,
                        null,
                        true
                )
        );
    }

    @Test
    @DisplayName("Should reject a yield transaction attributed to a human user")
    void shouldRejectHumanAttributedYield() {
        assertConstraintViolation(
                ACTOR_CONSTRAINT,
                () -> insertTransaction(
                        "YIELD",
                        new BigDecimal("10.00"),
                        savingsAccountId,
                        null,
                        userId,
                        false
                )
        );
    }

    @Test
    @DisplayName("Should accept a system-generated yield without a human actor")
    void shouldAcceptSystemGeneratedYield() {
        assertDoesNotThrow(() ->
                insertTransaction(
                        "YIELD",
                        new BigDecimal("10.00"),
                        savingsAccountId,
                        null,
                        null,
                        true
                )
        );
    }

    // ============================================================
    // Database fixtures
    // ============================================================

    private void insertTransaction(
            String type,
            BigDecimal amount,
            Long accountId,
            UUID transferCode,
            Long performedByUserId,
            boolean performedBySystem
    ) {
        jdbcTemplate.update(
                """
                                        INSERT INTO public.transactions (
                                            amount,
                                            created_at,
                                            transaction_type,
                                            account_id,
                                            transaction_code,
                                            transfer_code,
                                            balance_after,
                                            yield_date,
                                            performed_by_user_id,
                                            performed_by_system
                                        )
                                        VALUES (
                                            ?,
                                            CURRENT_TIMESTAMP,
                                            ?,
                                            ?,
                                            ?,
                                            CAST(? AS UUID),
                                            0.00,
                        CASE
                            WHEN ? = 'YIELD' THEN CURRENT_DATE
                        END,
                                            CAST(? AS BIGINT),
                                            ?
                                        )
                        """,
                amount,
                type,
                accountId,
                UUID.randomUUID(),
                transferCode,
                type,
                performedByUserId,
                performedBySystem
        );
    }

    private Long createUser() {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.users (
                            username,
                            email,
                            password,
                            role,
                            active,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            'constraint-user',
                            ?,
                            'not-a-real-hash',
                            'EMPLOYEE',
                            TRUE,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP
                        )
                        RETURNING id
                        """,
                Long.class,
                "constraint-user-" + UUID.randomUUID() + "@example.invalid"
        );
    }

    private Long createAccount(
            String accountType,
            String accountNumber
    ) {
        Long clientId = createClient();

        Long accountId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.accounts (
                            account_number,
                            balance,
                            status,
                            account_type,
                            client_id,
                            account_digit
                        )
                        VALUES (?, 0.00, 'ACTIVE', ?, ?, '0')
                        RETURNING id
                        """,
                Long.class,
                accountNumber,
                accountType,
                clientId
        );

        if ("CHECKING".equals(accountType)) {
            jdbcTemplate.update(
                    """
                            INSERT INTO public.checking_accounts (id)
                            VALUES (?)
                            """,
                    accountId
            );
        } else if ("SAVINGS".equals(accountType)) {
            jdbcTemplate.update(
                    """
                            INSERT INTO public.savings_accounts (
                                id,
                                last_yield_date,
                                last_yield_processed_date
                            )
                            VALUES (?, CURRENT_DATE - 1, CURRENT_DATE - 1)
                            """,
                    accountId
            );
        }

        return accountId;
    }

    private Long createClient() {
        int number = ++clientSequence;

        Long addressId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.addresses (
                            city,
                            complement,
                            neighborhood,
                            postal_code,
                            state,
                            street_name,
                            street_number
                        )
                        VALUES (
                            'Mogi das Cruzes',
                            NULL,
                            'Centro',
                            '08700000',
                            'SP',
                            'Rua de Teste',
                            ?
                        )
                        RETURNING id
                        """,
                Long.class,
                String.valueOf(number)
        );

        String cpf = String.format(
                "%011d",
                10_000_000_000L + number
        );

        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.clients (
                            cpf,
                            email,
                            name,
                            phone_number,
                            address_id
                        )
                        VALUES (?, ?, ?, '11999990000', ?)
                        RETURNING id
                        """,
                Long.class,
                cpf,
                "constraint-client-" + number + "@example.invalid",
                "Constraint Test Client " + number,
                addressId
        );
    }

    // ============================================================
    // Assertions
    // ============================================================

    private void assertConstraintViolation(
            String expectedConstraint,
            org.junit.jupiter.api.function.Executable operation
    ) {
        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                operation
        );

        StringBuilder messages = new StringBuilder();

        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {

            messages.append(cause.getMessage())
                    .append(System.lineSeparator());
        }

        assertTrue(
                messages.toString().contains(expectedConstraint),
                () -> "Expected constraint "
                        + expectedConstraint
                        + " but the database reported: "
                        + messages
        );
    }
}
