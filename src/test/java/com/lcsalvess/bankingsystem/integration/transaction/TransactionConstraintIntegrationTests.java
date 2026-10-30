package com.lcsalvess.bankingsystem.integration.transaction;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Transaction constraints integration")
class TransactionConstraintIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    private User user;
    private Client client;
    private CheckingAccount checkingAccount;
    private SavingsAccount savingsAccount;

    private String testId;
    private int clientSequence;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        clientSequence = 0;

        user = userRepository.saveAndFlush(
                new User(
                        "txc-" + testId,
                        "txc-" + testId + "@test.com",
                        "test-password",
                        Role.EMPLOYEE
                )
        );

        client = createClient();

        GeneratedAccountNumber checkingNumber =
                accountNumberGenerator.generate();

        checkingAccount = accountRepository.saveAndFlush(
                new CheckingAccount(
                        client,
                        checkingNumber.number(),
                        checkingNumber.digit()
                )
        );

        GeneratedAccountNumber savingsNumber =
                accountNumberGenerator.generate();

        savingsAccount = accountRepository.saveAndFlush(
                SavingsAccount.create(
                        client,
                        savingsNumber.number(),
                        savingsNumber.digit(),
                        LocalDate.now()
                )
        );
    }

    @AfterEach
    void tearDown() {
        if (checkingAccount != null) {
            jdbcTemplate.update(
                    "DELETE FROM transactions WHERE account_id = ?",
                    checkingAccount.getId()
            );

            accountRepository.delete(checkingAccount);
        }

        if (savingsAccount != null) {
            jdbcTemplate.update(
                    "DELETE FROM transactions WHERE account_id = ?",
                    savingsAccount.getId()
            );

            accountRepository.delete(savingsAccount);
        }

        if (client != null) {
            clientRepository.delete(client);
        }

        if (user != null) {
            userRepository.delete(user);
        }
    }

    @Test
    @DisplayName("Should reject transaction amounts above the maximum")
    void shouldRejectAmountAboveMaximum() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.DEPOSIT,
                        new BigDecimal("10000000000000.00"),
                        checkingAccount,
                        null,
                        user,
                        false,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should allow transaction amounts at the maximum")
    void shouldAllowAmountAtMaximum() {
        assertDoesNotThrow(
                () -> insertTransaction(
                        TransactionType.DEPOSIT,
                        new BigDecimal("9999999999999.99"),
                        checkingAccount,
                        null,
                        user,
                        false,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should require a transfer code for transfer transactions")
    void shouldRequireTransferCodeForTransfers() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.TRANSFER_SENT,
                        new BigDecimal("50.00"),
                        checkingAccount,
                        null,
                        user,
                        false,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should reject transfer codes for non-transfer transactions")
    void shouldRejectTransferCodeForNonTransferTransactions() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.DEPOSIT,
                        new BigDecimal("50.00"),
                        checkingAccount,
                        UUID.randomUUID(),
                        user,
                        false,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should allow one transaction for each transfer direction")
    void shouldAllowOneTransactionPerTransferDirection() {
        UUID transferCode = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        assertDoesNotThrow(() -> {
            insertTransaction(
                    TransactionType.TRANSFER_SENT,
                    amount,
                    checkingAccount,
                    transferCode,
                    user,
                    false,
                    null
            );

            insertTransaction(
                    TransactionType.TRANSFER_RECEIVED,
                    amount,
                    savingsAccount,
                    transferCode,
                    user,
                    false,
                    null
            );
        });
    }

    @Test
    @DisplayName("Should reject duplicate transfer directions")
    void shouldRejectDuplicateTransferDirection() {
        UUID transferCode = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        insertTransaction(
                TransactionType.TRANSFER_SENT,
                amount,
                checkingAccount,
                transferCode,
                user,
                false,
                null
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.TRANSFER_SENT,
                        amount,
                        savingsAccount,
                        transferCode,
                        user,
                        false,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should require a human actor for ordinary transactions")
    void shouldRequireHumanActorForOrdinaryTransactions() {
        assertDoesNotThrow(
                () -> insertTransaction(
                        TransactionType.DEPOSIT,
                        new BigDecimal("50.00"),
                        checkingAccount,
                        null,
                        user,
                        false,
                        null
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.DEPOSIT,
                        new BigDecimal("50.00"),
                        checkingAccount,
                        null,
                        null,
                        true,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should require a system actor for yield transactions")
    void shouldRequireSystemActorForYieldTransactions() {
        assertDoesNotThrow(
                () -> insertTransaction(
                        TransactionType.YIELD,
                        new BigDecimal("10.00"),
                        savingsAccount,
                        null,
                        null,
                        true,
                        LocalDate.now()
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTransaction(
                        TransactionType.YIELD,
                        new BigDecimal("10.00"),
                        savingsAccount,
                        null,
                        user,
                        false,
                        LocalDate.now().plusDays(1)
                )
        );
    }

    private void insertTransaction(
            TransactionType type,
            BigDecimal amount,
            Account account,
            UUID transferCode,
            User performedBy,
            boolean performedBySystem,
            LocalDate yieldDate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    amount,
                    created_at,
                    transaction_type,
                    account_id,
                    transaction_code,
                    transfer_code,
                    performed_by_user_id,
                    performed_by_system,
                    balance_after,
                    yield_date
                )
                VALUES (
                    ?,
                    CURRENT_TIMESTAMP,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                amount,
                type.name(),
                account.getId(),
                UUID.randomUUID(),
                transferCode,
                performedBy != null ? performedBy.getId() : null,
                performedBySystem,
                new BigDecimal("0.00"),
                yieldDate
        );
    }

    private Client createClient() {
        int number = ++clientSequence;

        Address address = new Address(
                "Rua Teste",
                String.valueOf(number),
                null,
                "Centro",
                "Mogi das Cruzes",
                State.SP,
                "08700000"
        );

        Client newClient = new Client(
                "Cliente Constraints " + number,
                String.format(
                        "%011d",
                        ThreadLocalRandom.current()
                                .nextLong(100_000_000_000L)
                ),
                "constraints-" + testId + "@test.com",
                "11999999999",
                address
        );

        return clientRepository.saveAndFlush(newClient);
    }
}
