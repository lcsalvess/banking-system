package com.lcsalvess.bankingsystem.integration.security;

import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.security.ChangePasswordRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.entity.transaction.Transaction;
import com.lcsalvess.bankingsystem.entity.transaction.TransactionType;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.AddressRepository;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.repository.TransactionRepository;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.service.account.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.GeneratedAccountNumber;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.service.security.PasswordChangeService;
import com.lcsalvess.bankingsystem.service.transaction.TransactionService;
import com.lcsalvess.bankingsystem.service.transaction.yield.DailyYieldService;
import com.lcsalvess.bankingsystem.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Roda os fluxos reais da aplicação com o datasource conectado como
 * {@code banking_app} (privilégios de UPDATE por coluna) e o Flyway como
 * {@code banking_migrator}, como em produção.
 *
 * <p>Os demais testes de integração rodam como dono do schema, então um
 * UPDATE em coluna sem GRANT só apareceria em produção. Aqui, qualquer fluxo
 * que suje uma coluna fora da política falha com "permission denied".
 *
 * <p>O perfil {@code privilege-e2e} não existe como arquivo: serve só para
 * sobrescrever um {@code SPRING_PROFILES_ACTIVE=test} herdado do ambiente e
 * manter ativos os componentes {@code @Profile("!test")}, como em produção.
 *
 * <p>O runtime não tem DELETE, então os dados criados aqui ficam no banco; os
 * identificadores são únicos por execução. Use um banco descartável. É
 * ignorado quando {@code PRIVILEGE_TEST_DB_URL} não está definida; o job
 * {@code least-privilege} do CI as define e provisiona as roles com
 * {@code scripts/db/bootstrap-roles.sql}.
 */
@SpringBootTest
@ActiveProfiles("privilege-e2e")
@EnabledIfEnvironmentVariable(named = "PRIVILEGE_TEST_DB_URL", matches = ".+")
@DisplayName("Application flows under the restricted runtime role")
class RuntimePrivilegesEndToEndTests {

    private static final String PASSWORD = "OldPass123!";
    private static final String NEW_PASSWORD = "NewPass456!";
    private static final String JWT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @DynamicPropertySource
    static void runtimeRoleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv("PRIVILEGE_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv("PRIVILEGE_TEST_APP_USER"));
        registry.add("spring.datasource.password",
                () -> System.getenv("PRIVILEGE_TEST_APP_PASSWORD"));

        registry.add("spring.flyway.url",
                () -> System.getenv("PRIVILEGE_TEST_DB_URL"));
        registry.add("spring.flyway.user",
                () -> System.getenv("PRIVILEGE_TEST_MIGRATOR_USER"));
        registry.add("spring.flyway.password",
                () -> System.getenv("PRIVILEGE_TEST_MIGRATOR_PASSWORD"));
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/callback");

        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("app.admin.bootstrap.required", () -> "false");
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private DailyYieldService dailyYieldService;

    @Autowired
    private PasswordChangeService passwordChangeService;

    @Autowired
    private UserService userService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoBean
    private CurrentUserService currentUserService;

    // ---------------------------------------------------------------------
    // Financial operations
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Deposit, withdrawal and transfer update balances and append to the ledger")
    void shouldRunFinancialOperations() {
        actAs(createUser(Role.EMPLOYEE));

        CheckingAccount source = createCheckingAccount("0.00");
        CheckingAccount destination = createCheckingAccount("0.00");

        transactionService.deposit(operation(source, "100.00"));
        transactionService.withdraw(operation(source, "30.00"));
        transactionService.transfer(new TransferRequestDTO(
                source.getAccountNumber(),
                source.getDigit(),
                destination.getAccountNumber(),
                destination.getDigit(),
                new BigDecimal("20.00")
        ));

        assertBalance("50.00", source);
        assertBalance("20.00", destination);
        assertEquals(3, countTransactions(source));
        assertEquals(1, countTransactions(destination));
    }

    @Test
    @DisplayName("Cancelling an empty account updates only its status")
    void shouldCancelEmptyAccount() {
        actAs(createUser(Role.EMPLOYEE));

        CheckingAccount account = createCheckingAccount("0.00");

        accountService.cancel(account.getAccountNumber(), account.getDigit());

        assertEquals(
                AccountStatus.CANCELLED,
                accountRepository.findById(account.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    @DisplayName("Daily yield credits a savings account and records the system transaction")
    void shouldApplyDailyYield() {
        User employee = createUser(Role.EMPLOYEE);
        actAs(employee);

        LocalDate today = LocalDate.now(clock);
        LocalDate referenceDate = today.minusDays(1);

        SavingsAccount savings = createSavingsAccount(
                "1000.00",
                today.minusDays(2)
        );

        // Ledger entry from before the reference day, so the yield has a balance to apply to.
        transactionRepository.saveAndFlush(
                Transaction.create(
                        TransactionType.DEPOSIT,
                        new BigDecimal("1000.00"),
                        clock.instant().minus(3, ChronoUnit.DAYS),
                        savings,
                        employee,
                        savings.getBalance()
                )
        );

        // The startup recovery runs in the background and may process the same
        // date first, so the assertions are on the resulting state.
        dailyYieldService.processDailyYieldForDate(
                savings.getAccountNumber(),
                savings.getDigit(),
                referenceDate
        );

        Integer yields = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM transactions
                WHERE account_id = ?
                  AND transaction_type = 'YIELD'
                  AND yield_date = ?
                """,
                Integer.class,
                savings.getId(),
                referenceDate
        );

        assertEquals(1, yields);

        Account reloaded = accountRepository.findById(savings.getId())
                .orElseThrow();

        assertTrue(
                reloaded.getBalance().compareTo(new BigDecimal("1000.00")) > 0,
                "The yield should have increased the balance"
        );
    }

    // ---------------------------------------------------------------------
    // Users and authentication
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Changing the password updates the hash and the token version")
    void shouldChangePassword() {
        User user = createUser(Role.EMPLOYEE);
        actAs(user);

        passwordChangeService.changePassword(
                new ChangePasswordRequestDTO(PASSWORD, NEW_PASSWORD)
        );

        User reloaded = userRepository.findById(user.getId()).orElseThrow();

        assertEquals(1, reloaded.getTokenVersion());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPassword()));
    }

    @Test
    @DisplayName("Deactivating a user updates the active flag and the token version")
    void shouldDeactivateUser() {
        actAs(createUser(Role.ADMIN));

        User target = createUser(Role.EMPLOYEE);

        userService.deactivate(target.getId());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();

        assertFalse(reloaded.isActive());
        assertEquals(1, reloaded.getTokenVersion());
    }

    // ---------------------------------------------------------------------
    // Clients and addresses
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Updating client contact data touches only the granted columns")
    void shouldUpdateClientContactData() {
        actAs(createUser(Role.EMPLOYEE));

        Client client = createClient();
        String newEmail = "e2e-updated-" + shortId() + "@test.com";

        clientService.update(
                client.getId(),
                new ClientUpdateRequestDTO(null, newEmail, "11988887777", null)
        );

        Client reloaded = clientRepository.findById(client.getId())
                .orElseThrow();

        assertEquals(newEmail, reloaded.getEmail());
        assertEquals("11988887777", reloaded.getPhoneNumber());
    }

    @Test
    @DisplayName("Updating every mutable address column is allowed")
    void shouldUpdateAddress() {
        Client client = createClient();
        Long addressId = client.getAddress().getId();

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> addressRepository.findById(addressId)
                        .orElseThrow()
                        .updateFrom(
                                "Rua Atualizada",
                                "99",
                                "Apto 2",
                                "Bairro Novo",
                                "Suzano",
                                State.RJ,
                                "20000000"
                        )
        );

        Address reloaded = addressRepository.findById(addressId).orElseThrow();

        assertEquals("Rua Atualizada", reloaded.getStreetName());
        assertEquals("Suzano", reloaded.getCity());
        assertEquals(State.RJ, reloaded.getState());
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private void actAs(User user) {
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(currentUserService.findCurrentUser())
                .thenReturn(Optional.of(user));
        when(currentUserService.getUsername()).thenReturn(user.getUsername());
    }

    private User createUser(Role role) {
        String id = shortId();

        return userRepository.saveAndFlush(
                new User(
                        "e2e-" + id,
                        "e2e-" + id + "@test.com",
                        passwordEncoder.encode(PASSWORD),
                        role
                )
        );
    }

    private Client createClient() {
        String id = shortId();

        Address address = new Address(
                "Rua E2E",
                "1",
                null,
                "Centro",
                "Mogi das Cruzes",
                State.SP,
                "08700000"
        );

        Client client = new Client(
                "Cliente E2E " + id,
                String.format(
                        "%011d",
                        ThreadLocalRandom.current().nextLong(100_000_000_000L)
                ),
                "e2e-client-" + id + "@test.com",
                "11999999999",
                address
        );

        return clientRepository.saveAndFlush(client);
    }

    private CheckingAccount createCheckingAccount(String initialBalance) {
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        CheckingAccount account = new CheckingAccount(
                createClient(),
                generated.number(),
                generated.digit()
        );

        BigDecimal balance = new BigDecimal(initialBalance);

        if (balance.signum() > 0) {
            account.credit(balance);
        }

        return accountRepository.saveAndFlush(account);
    }

    private SavingsAccount createSavingsAccount(
            String initialBalance,
            LocalDate initialYieldDate
    ) {
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        SavingsAccount account = SavingsAccount.create(
                createClient(),
                generated.number(),
                generated.digit(),
                initialYieldDate
        );

        BigDecimal balance = new BigDecimal(initialBalance);

        if (balance.signum() > 0) {
            account.credit(balance);
        }

        return accountRepository.saveAndFlush(account);
    }

    private AccountOperationRequestDTO operation(
            Account account,
            String amount
    ) {
        return new AccountOperationRequestDTO(
                account.getAccountNumber(),
                account.getDigit(),
                new BigDecimal(amount)
        );
    }

    private void assertBalance(String expected, Account account) {
        BigDecimal actual = accountRepository.findById(account.getId())
                .orElseThrow()
                .getBalance();

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Expected balance " + expected + " but was " + actual
        );
    }

    private int countTransactions(Account account) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transactions WHERE account_id = ?",
                Integer.class,
                account.getId()
        );

        return total == null ? 0 : total;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
