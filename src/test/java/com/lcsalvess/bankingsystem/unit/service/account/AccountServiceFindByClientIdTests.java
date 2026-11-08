package com.lcsalvess.bankingsystem.unit.service.account;

import com.lcsalvess.bankingsystem.dto.response.account.AccountResponseDTO;
import com.lcsalvess.bankingsystem.entity.account.Account;
import com.lcsalvess.bankingsystem.entity.account.AccountStatus;
import com.lcsalvess.bankingsystem.entity.account.AccountType;
import com.lcsalvess.bankingsystem.entity.account.CheckingAccount;
import com.lcsalvess.bankingsystem.entity.account.SavingsAccount;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.AccountRepository;
import com.lcsalvess.bankingsystem.repository.specification.AccountSpecifications;
import com.lcsalvess.bankingsystem.service.account.AccountService;
import com.lcsalvess.bankingsystem.service.account.generator.AccountNumberGenerator;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceFindByClientIdTests {

    private static final Long CLIENT_ID = 1L;
    private static final String CLIENT_NAME = "Cliente Teste";

    private static final String CHECKING_NUMBER = "00001";
    private static final String CHECKING_DIGIT = "9";
    private static final String SAVINGS_NUMBER = "00002";
    private static final String SAVINGS_DIGIT = "7";

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-02T02:30:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClientService clientService;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                accountNumberGenerator,
                clientService,
                currentUserService,
                eventPublisher,
                CLOCK
        );
    }

    @Nested
    @DisplayName("findByClientId(Long, AccountType, AccountStatus, Pageable)")
    class FindByClientId {

        @Test
        @DisplayName("Should return the client's accounts as a page of DTOs")
        void shouldReturnPageOfClientAccounts() {
            Pageable pageable = PageRequest.of(
                    0,
                    10,
                    Sort.by("accountNumber").ascending().and(Sort.by("id").ascending())
            );
            Specification<Account> specification = (root, query, cb) -> null;

            when(accountRepository.findAll(specification, pageable))
                    .thenReturn(new PageImpl<>(
                            List.of(checkingAccount(), savingsAccount()),
                            pageable,
                            2
                    ));

            try (MockedStatic<AccountSpecifications> specifications =
                         mockStatic(AccountSpecifications.class)) {

                specifications.when(() ->
                        AccountSpecifications.byClientAndFilters(CLIENT_ID, null, null)
                ).thenReturn(specification);

                Page<AccountResponseDTO> result =
                        accountService.findByClientId(CLIENT_ID, null, null, pageable);

                assertEquals(
                        List.of(
                                response(CHECKING_NUMBER, CHECKING_DIGIT, AccountType.CHECKING),
                                response(SAVINGS_NUMBER, SAVINGS_DIGIT, AccountType.SAVINGS)
                        ),
                        result.getContent()
                );
                assertEquals(2, result.getTotalElements());
                assertEquals(1, result.getTotalPages());

                specifications.verify(() ->
                                AccountSpecifications.byClientAndFilters(CLIENT_ID, null, null),
                        times(1)
                );
            }

            verify(clientService).findEntityById(CLIENT_ID);
            verify(accountRepository).findAll(specification, pageable);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should build the specification with the type and status filters")
        void shouldBuildSpecificationWithFilters() {
            Pageable pageable = PageRequest.of(
                    0,
                    10,
                    Sort.by("accountNumber").ascending().and(Sort.by("id").ascending())
            );
            Specification<Account> specification = (root, query, cb) -> null;

            when(accountRepository.findAll(specification, pageable))
                    .thenReturn(new PageImpl<>(List.of(savingsAccount()), pageable, 1));

            try (MockedStatic<AccountSpecifications> specifications =
                         mockStatic(AccountSpecifications.class)) {

                specifications.when(() ->
                        AccountSpecifications.byClientAndFilters(
                                CLIENT_ID,
                                AccountType.SAVINGS,
                                AccountStatus.ACTIVE
                        )
                ).thenReturn(specification);

                Page<AccountResponseDTO> result = accountService.findByClientId(
                        CLIENT_ID,
                        AccountType.SAVINGS,
                        AccountStatus.ACTIVE,
                        pageable
                );

                assertEquals(
                        List.of(response(SAVINGS_NUMBER, SAVINGS_DIGIT, AccountType.SAVINGS)),
                        result.getContent()
                );

                specifications.verify(() ->
                                AccountSpecifications.byClientAndFilters(
                                        CLIENT_ID,
                                        AccountType.SAVINGS,
                                        AccountStatus.ACTIVE
                                ),
                        times(1)
                );
            }

            verify(clientService).findEntityById(CLIENT_ID);
            verify(accountRepository).findAll(specification, pageable);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return an empty page when the client has no accounts")
        void shouldReturnEmptyPageWhenClientHasNoAccounts() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());

            when(accountRepository.findAll(
                    ArgumentMatchers.<Specification<Account>>any(),
                    ArgumentMatchers.any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<AccountResponseDTO> result =
                    accountService.findByClientId(CLIENT_ID, null, null, pageable);

            assertTrue(result.isEmpty());
            assertEquals(0, result.getTotalElements());
            assertEquals(0, result.getTotalPages());

            verify(clientService).findEntityById(CLIENT_ID);
        }

        @Test
        @DisplayName("Should append the id tie-breaker when the sort has no id")
        void shouldAppendIdTieBreakerWhenSortHasNoId() {
            Pageable requested = PageRequest.of(0, 10, Sort.by("balance").descending());

            when(accountRepository.findAll(
                    ArgumentMatchers.<Specification<Account>>any(),
                    ArgumentMatchers.any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(), requested, 0));

            accountService.findByClientId(CLIENT_ID, null, null, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            verify(accountRepository).findAll(
                    ArgumentMatchers.<Specification<Account>>any(),
                    captor.capture()
            );

            assertEquals(
                    Sort.by(Sort.Order.desc("balance"), Sort.Order.asc("id")),
                    captor.getValue().getSort()
            );
            assertEquals(0, captor.getValue().getPageNumber());
            assertEquals(10, captor.getValue().getPageSize());
        }

        @Test
        @DisplayName("Should not duplicate the id tie-breaker when the sort already has id")
        void shouldKeepSortWhenItAlreadyHasId() {
            Pageable requested = PageRequest.of(1, 5, Sort.by("id").descending());

            when(accountRepository.findAll(
                    ArgumentMatchers.<Specification<Account>>any(),
                    ArgumentMatchers.any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(), requested, 0));

            accountService.findByClientId(CLIENT_ID, null, null, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            verify(accountRepository).findAll(
                    ArgumentMatchers.<Specification<Account>>any(),
                    captor.capture()
            );

            assertEquals(Sort.by(Sort.Order.desc("id")), captor.getValue().getSort());
            assertEquals(1, captor.getValue().getPageNumber());
            assertEquals(5, captor.getValue().getPageSize());
        }

        @Test
        @DisplayName("Should throw ClientNotFoundException without querying accounts")
        void shouldThrowWhenClientDoesNotExist() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());

            when(clientService.findEntityById(CLIENT_ID))
                    .thenThrow(new ClientNotFoundException());

            ClientNotFoundException exception = assertThrows(
                    ClientNotFoundException.class,
                    () -> accountService.findByClientId(CLIENT_ID, null, null, pageable)
            );

            assertEquals(ApiErrorMessages.CLIENT_NOT_FOUND, exception.getMessage());

            verify(clientService).findEntityById(CLIENT_ID);
            verifyNoInteractions(accountRepository);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should run in a read-only transaction")
        void shouldRunInReadOnlyTransaction() throws NoSuchMethodException {
            Method method = AccountService.class.getMethod(
                    "findByClientId",
                    Long.class,
                    AccountType.class,
                    AccountStatus.class,
                    Pageable.class
            );

            Transactional transactional =
                    AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

            assertNotNull(transactional);
            assertTrue(transactional.readOnly());
        }
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                accountRepository,
                clientService,
                accountNumberGenerator,
                currentUserService,
                eventPublisher
        );
    }

    private static Client client() {
        Address address = new Address(
                "Rua Teste", "123", null, "Centro", "Mogi das Cruzes", State.SP, "08710000"
        );
        Client client = new Client(CLIENT_NAME, "52998224725", "teste@email.com", "11999999999", address);
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        return client;
    }

    private static CheckingAccount checkingAccount() {
        CheckingAccount account = new CheckingAccount(client(), CHECKING_NUMBER, CHECKING_DIGIT);
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }

    private static SavingsAccount savingsAccount() {
        SavingsAccount account = SavingsAccount.create(client(), SAVINGS_NUMBER, SAVINGS_DIGIT, TODAY);
        ReflectionTestUtils.setField(account, "id", 2L);
        return account;
    }

    private static AccountResponseDTO response(String number, String digit, AccountType type) {
        return new AccountResponseDTO(
                number, digit, CLIENT_NAME, BigDecimal.ZERO, type, AccountStatus.ACTIVE
        );
    }
}
