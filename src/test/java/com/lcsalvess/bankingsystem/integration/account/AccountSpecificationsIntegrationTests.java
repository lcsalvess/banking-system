package com.lcsalvess.bankingsystem.integration.account;

import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.repository.specification.AccountSpecifications;
import com.lcsalvess.bankingsystem.service.account.generator.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.account.generator.GeneratedAccountNumber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test creates its own clients with random CPF and e-mail and runs inside
 * a rolled-back transaction, so rows that already exist in the test database
 * do not interfere with the results.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@DisplayName("AccountSpecifications integration tests")
class AccountSpecificationsIntegrationTests {

    private static final Pageable BY_ID = PageRequest.of(0, 50, Sort.by("id").ascending());

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Should return only the accounts of the given client")
    void shouldReturnOnlyAccountsOfTheGivenClient() {
        Client clientA = persistClient();
        Client clientB = persistClient();

        Account checkingA = persistChecking(clientA, false);
        Account savingsA = persistSavings(clientA, false);
        persistChecking(clientB, false);

        Page<Account> result = find(clientA.getId(), null, null, BY_ID);

        assertEquals(
                Set.of(checkingA.getAccountNumber(), savingsA.getAccountNumber()),
                numbersOf(result)
        );
        assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Should filter by account type")
    void shouldFilterByType() {
        Client client = persistClient();

        Account checking = persistChecking(client, false);
        persistSavings(client, false);

        Page<Account> result = find(client.getId(), AccountType.CHECKING, null, BY_ID);

        assertEquals(Set.of(checking.getAccountNumber()), numbersOf(result));
    }

    @Test
    @DisplayName("Should filter by account status")
    void shouldFilterByStatus() {
        Client client = persistClient();

        Account cancelledChecking = persistChecking(client, true);
        Account activeChecking = persistChecking(client, false);
        Account activeSavings = persistSavings(client, false);

        Page<Account> cancelled = find(client.getId(), null, AccountStatus.CANCELLED, BY_ID);
        Page<Account> active = find(client.getId(), null, AccountStatus.ACTIVE, BY_ID);

        assertEquals(Set.of(cancelledChecking.getAccountNumber()), numbersOf(cancelled));
        assertEquals(
                Set.of(activeChecking.getAccountNumber(), activeSavings.getAccountNumber()),
                numbersOf(active)
        );
    }

    @Test
    @DisplayName("Should combine the type and status filters")
    void shouldCombineTypeAndStatusFilters() {
        Client client = persistClient();

        persistChecking(client, true);
        Account activeChecking = persistChecking(client, false);
        persistSavings(client, false);

        Page<Account> result = find(
                client.getId(),
                AccountType.CHECKING,
                AccountStatus.ACTIVE,
                BY_ID
        );

        assertEquals(Set.of(activeChecking.getAccountNumber()), numbersOf(result));
    }

    @Test
    @DisplayName("Should return an empty page for a client without accounts or an unknown client")
    void shouldReturnEmptyPageWhenThereAreNoAccounts() {
        Client clientWithoutAccounts = persistClient();

        Page<Account> withoutAccounts = find(clientWithoutAccounts.getId(), null, null, BY_ID);
        Page<Account> unknownClient = find(Long.MAX_VALUE, null, null, BY_ID);

        assertTrue(withoutAccounts.isEmpty());
        assertEquals(0, withoutAccounts.getTotalElements());
        assertTrue(unknownClient.isEmpty());
        assertEquals(0, unknownClient.getTotalElements());
    }

    @Test
    @DisplayName("Should paginate and count only the matching accounts")
    void shouldPaginateMatchingAccounts() {
        Client client = persistClient();
        Client otherClient = persistClient();

        persistChecking(client, true);
        persistChecking(client, false);
        persistSavings(client, false);
        persistChecking(otherClient, false);

        Pageable firstPage = PageRequest.of(0, 2, Sort.by("id").ascending());
        Pageable secondPage = PageRequest.of(1, 2, Sort.by("id").ascending());

        Page<Account> first = find(client.getId(), null, null, firstPage);
        Page<Account> second = find(client.getId(), null, null, secondPage);

        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(2, first.getContent().size());
        assertEquals(1, second.getContent().size());
    }

    @Test
    @DisplayName("Should sort by a whitelisted property of the joined-inheritance entity")
    void shouldSortByType() {
        Client client = persistClient();

        persistChecking(client, false);
        persistSavings(client, false);

        Pageable sortedByTypeDescending = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("type"), Sort.Order.asc("id"))
        );

        Page<Account> result = find(client.getId(), null, null, sortedByTypeDescending);

        assertEquals(
                List.of(AccountType.SAVINGS, AccountType.CHECKING),
                result.getContent().stream().map(Account::getType).toList()
        );
    }

    private Page<Account> find(
            Long clientId,
            AccountType type,
            AccountStatus status,
            Pageable pageable
    ) {
        // Reads from the database instead of the persistence context.
        entityManager.clear();

        return accountRepository.findAll(
                AccountSpecifications.byClientAndFilters(clientId, type, status),
                pageable
        );
    }

    private static Set<String> numbersOf(Page<Account> page) {
        return page.getContent().stream()
                .map(Account::getAccountNumber)
                .collect(Collectors.toSet());
    }

    private Client persistClient() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Address address = new Address(
                "Rua Teste",
                "1",
                null,
                "Centro",
                "Mogi das Cruzes",
                State.SP,
                "08700000"
        );

        Client client = new Client(
                "Cliente " + marker,
                String.format("%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L)),
                "spec" + marker + "@test.com",
                "11999999999",
                address
        );

        return clientRepository.saveAndFlush(client);
    }

    private Account persistChecking(Client client, boolean cancelled) {
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        CheckingAccount account = new CheckingAccount(
                client,
                generated.number(),
                generated.digit()
        );

        if (cancelled) {
            account.cancel();
        }

        return accountRepository.saveAndFlush(account);
    }

    private Account persistSavings(Client client, boolean cancelled) {
        GeneratedAccountNumber generated = accountNumberGenerator.generate();

        SavingsAccount account = SavingsAccount.create(
                client,
                generated.number(),
                generated.digit(),
                LocalDate.now(clock)
        );

        if (cancelled) {
            account.cancel();
        }

        return accountRepository.saveAndFlush(account);
    }
}
