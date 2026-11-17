package com.lcsalvess.bankingsystem.unit.service.client;

import com.lcsalvess.bankingsystem.dto.request.address.AddressRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.address.AddressUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.client.ClientRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.address.AddressResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.client.ClientResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.client.ClientSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.service.address.AddressData;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import com.lcsalvess.bankingsystem.service.client.ClientPersistenceService;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.lcsalvess.bankingsystem.testsupport.ClientTestFactory.aClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.service.client.ClientServiceTests#";

    private static final String USERNAME = "test.user";

    private static final Long CLIENT_ID = 1L;
    private static final String POSTAL_CODE = "01001000";

    private static final String UPDATED_NAME = "Jane Doe Update";
    private static final String UPDATED_EMAIL = "jane.doe.update@example.com";
    private static final String UPDATED_PHONE = "11888888888";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientPersistenceService clientPersistenceService;

    @Mock
    private AddressService addressService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ClientService clientService;

    private Client baseClient;
    private String cpf;

    @BeforeEach
    void setUp() {
        baseClient = aClient("base-client")
                .withId(CLIENT_ID)
                .build();
        cpf = baseClient.getCpf();
    }

    @Nested
    @DisplayName("findAll(String, Pageable)")
    class FindAll {

        private final Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by("id").ascending()
        );

        @Test
        @DisplayName("Should return a page of clients when no name filter is provided")
        void shouldReturnPageOfClientsWithoutNameFilter() {
            Client secondClient = aClient("second-client")
                    .withId(2L)
                    .withName("Maria Silva")
                    .build();

            Page<Client> clients = new PageImpl<>(
                    List.of(baseClient, secondClient),
                    pageable,
                    2
            );

            when(clientRepository.findAll(pageable)).thenReturn(clients);

            Page<ClientSummaryResponseDTO> result =
                    clientService.findAll(null, pageable);

            assertEquals(
                    List.of(
                            clientSummaryResponse(baseClient),
                            clientSummaryResponse(secondClient)
                    ),
                    result.getContent()
            );
            assertEquals(2, result.getTotalElements());
            assertEquals(1, result.getTotalPages());
            assertEquals(0, result.getNumber());
            assertEquals(10, result.getSize());

            verify(clientRepository).findAll(pageable);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return an empty page when there are no clients")
        void shouldReturnEmptyPageWhenThereAreNoClients() {
            Page<Client> emptyPage = new PageImpl<>(
                    List.of(),
                    pageable,
                    0
            );

            when(clientRepository.findAll(pageable)).thenReturn(emptyPage);

            Page<ClientSummaryResponseDTO> result =
                    clientService.findAll(null, pageable);

            assertTrue(result.isEmpty());
            assertEquals(0, result.getTotalElements());
            assertEquals(0, result.getTotalPages());

            verify(clientRepository).findAll(pageable);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should filter clients by name ignoring case")
        void shouldFilterClientsByNameIgnoringCase() {
            Client matchingClient = aClient("matching-client")
                    .withId(CLIENT_ID)
                    .withName("Emily Johnson")
                    .build();

            Page<Client> clients = new PageImpl<>(
                    List.of(matchingClient),
                    pageable,
                    1
            );

            when(clientRepository.findByNameContainingIgnoreCase(
                    "emily",
                    pageable
            )).thenReturn(clients);

            Page<ClientSummaryResponseDTO> result =
                    clientService.findAll("  emily  ", pageable);

            assertEquals(
                    List.of(clientSummaryResponse(matchingClient)),
                    result.getContent()
            );
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getTotalPages());

            verify(clientRepository).findByNameContainingIgnoreCase(
                    "emily",
                    pageable
            );
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should list all clients when name filter is blank")
        void shouldListAllClientsWhenNameIsBlank() {
            Page<Client> clients = new PageImpl<>(
                    List.of(baseClient),
                    pageable,
                    1
            );

            when(clientRepository.findAll(pageable)).thenReturn(clients);

            Page<ClientSummaryResponseDTO> result =
                    clientService.findAll("   ", pageable);

            assertEquals(1, result.getTotalElements());

            verify(clientRepository).findAll(pageable);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findEntityById(Long)")
    class FindEntityById {

        @Test
        @DisplayName("Should return the client entity when ID exists")
        void shouldReturnClientEntityWhenIdExists() {
            stubClientLookup(baseClient);

            Client result = clientService.findEntityById(CLIENT_ID);

            assertSame(baseClient, result);

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    ApiErrorMessages.CLIENT_NOT_FOUND,
                    () -> clientService.findEntityById(CLIENT_ID)
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findById(Long)")
    class FindById {

        @Test
        @DisplayName("Should return the client response when ID exists")
        void shouldReturnClientResponseWhenIdExists() {
            stubClientLookup(baseClient);

            ClientResponseDTO result = clientService.findById(CLIENT_ID);

            assertEquals(clientResponse(baseClient), result);

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    ApiErrorMessages.CLIENT_NOT_FOUND,
                    () -> clientService.findById(CLIENT_ID)
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("create(ClientRequestDTO)")
    class Create {

        @Test
        @DisplayName("Should look up the address before calling the persistence service")
        void shouldCreateClientSuccessfully() {
            ClientRequestDTO request = clientRequest();
            Address address = baseClient.getAddress();

            when(clientRepository.existsByCpf(cpf)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(any(Client.class), eq(USERNAME)))
                    .thenReturn(baseClient);

            ClientResponseDTO result = clientService.create(request);

            ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);

            assertNotNull(result);
            assertEquals(baseClient.getId(), result.id());

            InOrder inOrder = inOrder(
                    clientRepository,
                    addressService,
                    currentUserService,
                    clientPersistenceService
            );
            inOrder.verify(clientRepository).existsByCpf(cpf);
            inOrder.verify(addressService).createFromPostalCode(request.address());
            inOrder.verify(currentUserService).getUsername();
            inOrder.verify(clientPersistenceService).create(clientCaptor.capture(), eq(USERNAME));

            Client capturedClient = clientCaptor.getValue();

            assertEquals(baseClient.getName(), capturedClient.getName());
            assertEquals(cpf, capturedClient.getCpf());
            assertEquals(baseClient.getEmail(), capturedClient.getEmail());
            assertEquals(baseClient.getPhoneNumber(), capturedClient.getPhoneNumber());
            assertSame(address, capturedClient.getAddress());

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when CPF already exists")
        void shouldThrowWhenCpfAlreadyExists() {
            ClientRequestDTO request = clientRequest();

            when(clientRepository.existsByCpf(cpf)).thenReturn(true);

            assertThrowsWithMessage(
                    ClientCpfAlreadyExistsException.class,
                    ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,
                    () -> clientService.create(request)
            );

            verify(clientRepository).existsByCpf(cpf);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should translate the integrity violation when the email already exists")
        void shouldTranslateViolationWhenEmailAlreadyExists() {
            ClientRequestDTO request = clientRequest();
            Address address = baseClient.getAddress();

            ConstraintViolationException cause =
                    new ConstraintViolationException(
                            "Unique constraint violation",
                            null,
                            "uk_clients_email"
                    );

            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException(
                            "Could not persist client",
                            cause
                    );

            when(clientRepository.existsByCpf(cpf)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(any(Client.class), eq(USERNAME)))
                    .thenThrow(exception);

            ClientEmailAlreadyExistsException thrown = assertThrows(
                    ClientEmailAlreadyExistsException.class,
                    () -> clientService.create(request)
            );

            assertEquals(ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS, thrown.getMessage());
            assertSame(exception, thrown.getCause());

            verify(clientRepository).existsByCpf(cpf);
            verify(addressService).createFromPostalCode(request.address());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).create(any(Client.class), eq(USERNAME));
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "addressLookupFailures")
        @DisplayName("Should propagate the failure and not persist when address lookup fails")
        void shouldPropagateFailureWhenAddressLookupFails(
                String scenario,
                RuntimeException exception
        ) {
            ClientRequestDTO request = clientRequest();

            when(clientRepository.existsByCpf(cpf)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address()))
                    .thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.create(request)
            );

            assertSame(exception, thrown);

            verify(clientRepository).existsByCpf(cpf);
            verify(addressService).createFromPostalCode(request.address());
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should rethrow the failure when persistence fails and the CPF does not exist")
        void shouldRethrowWhenPersistenceFailsForOtherReasons() {
            ClientRequestDTO request = clientRequest();
            Address address = baseClient.getAddress();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("fk_client_address");

            when(clientRepository.existsByCpf(cpf)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(any(Client.class), eq(USERNAME)))
                    .thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.create(request)
            );

            assertSame(exception, thrown);

            verify(clientRepository).existsByCpf(cpf);
            verify(addressService).createFromPostalCode(request.address());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).create(any(Client.class), eq(USERNAME));
            verifyNoMoreInteractionsOnMocks();
        }

        // Race: the initial existsByCpf check passed, but another request
        // persisted the same CPF before the commit.
        @Test
        @DisplayName("Should translate the integrity violation when the CPF was taken concurrently")
        void shouldTranslateViolationWhenCpfWasTakenConcurrently() {
            ClientRequestDTO request = clientRequest();
            Address address = baseClient.getAddress();

            ConstraintViolationException cause =
                    new ConstraintViolationException(
                            "Unique constraint violation",
                            null,
                            "uk_clients_cpf"
                    );

            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException(
                            "Could not persist client",
                            cause
                    );

            when(clientRepository.existsByCpf(cpf)).thenReturn(false, true);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(any(Client.class), eq(USERNAME)))
                    .thenThrow(exception);

            assertThrowsWithMessage(
                    ClientCpfAlreadyExistsException.class,
                    ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,
                    () -> clientService.create(request)
            );

            verify(clientRepository).existsByCpf(cpf);
            verify(addressService).createFromPostalCode(request.address());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).create(any(Client.class), eq(USERNAME));
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("update(Long, ClientUpdateRequestDTO)")
    class Update {

        @Test
        @DisplayName("Should look up the address before calling the persistence service")
        void shouldUpdateClientAndAddressSuccessfully() {
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();

            ClientUpdateData expectedData = new ClientUpdateData(
                    UPDATED_NAME,
                    UPDATED_EMAIL,
                    UPDATED_PHONE,
                    new AddressUpdateData(addressData, "456", "Apto 22")
            );

            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenReturn(addressData);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.update(CLIENT_ID, expectedData, USERNAME))
                    .thenReturn(baseClient);

            ClientResponseDTO result = clientService.update(CLIENT_ID, request);

            assertNotNull(result);
            assertEquals(baseClient.getId(), result.id());

            InOrder inOrder = inOrder(
                    addressService,
                    currentUserService,
                    clientPersistenceService
            );

            inOrder.verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            inOrder.verify(currentUserService).getUsername();
            inOrder.verify(clientPersistenceService).update(
                    CLIENT_ID,
                    expectedData,
                    USERNAME
            );

            verifyNoMoreInteractionsOnMocks();
        }

        // Every scenario has address = null: the service must not look up
        // the address and must forward address = null.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "partialUpdates")
        @DisplayName("Should forward updates without address and without looking it up")
        void shouldForwardUpdatesWithoutLookingUpTheAddress(
                String scenario,
                ClientUpdateRequestDTO request,
                ClientUpdateData expectedData
        ) {
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.update(CLIENT_ID, expectedData, USERNAME))
                    .thenReturn(baseClient);

            ClientResponseDTO result = clientService.update(CLIENT_ID, request);

            assertNotNull(result);
            assertEquals(baseClient.getId(), result.id());

            verify(currentUserService).getUsername();
            verify(clientPersistenceService).update(
                    CLIENT_ID,
                    expectedData,
                    USERNAME
            );

            verifyNoMoreInteractionsOnMocks();
        }

        // The service only forwards streetNumber/complement; normalization
        // (blank complement becomes null) is the entity's responsibility.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "addressForwarding")
        @DisplayName("Should forward street number and complement from the address request")
        void shouldForwardStreetNumberAndComplement(
                String scenario,
                AddressUpdateRequestDTO addressRequest,
                String expectedStreetNumber,
                String expectedComplement
        ) {
            AddressData addressData = addressData();

            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(
                    null,
                    null,
                    null,
                    addressRequest
            );

            ClientUpdateData expectedData = new ClientUpdateData(
                    null,
                    null,
                    null,
                    new AddressUpdateData(
                            addressData,
                            expectedStreetNumber,
                            expectedComplement
                    )
            );

            when(addressService.findAddressByPostalCode(addressRequest.postalCode()))
                    .thenReturn(addressData);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.update(CLIENT_ID, expectedData, USERNAME))
                    .thenReturn(baseClient);

            ClientResponseDTO result = clientService.update(CLIENT_ID, request);

            assertNotNull(result);
            assertEquals(baseClient.getId(), result.id());

            verify(addressService).findAddressByPostalCode(addressRequest.postalCode());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).update(CLIENT_ID, expectedData, USERNAME);

            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "addressLookupFailures")
        @DisplayName("Should propagate the failure and not persist when address lookup fails")
        void shouldPropagateFailureWhenAddressLookupFails(
                String scenario,
                RuntimeException exception
        ) {
            ClientUpdateRequestDTO request = fullUpdateRequest();

            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.update(CLIENT_ID, request)
            );

            assertSame(exception, thrown);

            InOrder inOrder = inOrder(
                    addressService
            );

            inOrder.verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );

            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should propagate the failure when persistence fails")
        void shouldPropagateFailureWhenPersistenceFails() {
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();

            ClientUpdateData expectedData = new ClientUpdateData(
                    UPDATED_NAME,
                    UPDATED_EMAIL,
                    UPDATED_PHONE,
                    new AddressUpdateData(addressData, "456", "Apto 22")
            );

            ClientNotFoundException exception =
                    new ClientNotFoundException(ApiErrorMessages.CLIENT_NOT_FOUND);

            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenReturn(addressData);

            when(currentUserService.getUsername()).thenReturn(USERNAME);

            when(clientPersistenceService.update(CLIENT_ID, expectedData, USERNAME))
                    .thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.update(CLIENT_ID, request)
            );

            assertSame(exception, thrown);

            InOrder inOrder = inOrder(
                    addressService,
                    currentUserService,
                    clientPersistenceService
            );

            inOrder.verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            inOrder.verify(currentUserService).getUsername();
            inOrder.verify(clientPersistenceService)
                    .update(CLIENT_ID, expectedData, USERNAME);

            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transaction boundaries")
    class TransactionBoundaries {

        // The write transaction belongs to ClientPersistenceService.
        // If create/update (or the class) become @Transactional here again,
        // the connection would stay open during the postal code lookup.
        @Test
        @DisplayName("create and update should not run inside a transaction")
        void createAndUpdateShouldNotBeTransactional() throws NoSuchMethodException {
            Method create = ClientService.class.getMethod("create", ClientRequestDTO.class);
            Method update = ClientService.class.getMethod(
                    "update",
                    Long.class,
                    ClientUpdateRequestDTO.class
            );

            assertFalse(AnnotatedElementUtils.hasAnnotation(ClientService.class, Transactional.class));
            assertFalse(AnnotatedElementUtils.hasAnnotation(create, Transactional.class));
            assertFalse(AnnotatedElementUtils.hasAnnotation(update, Transactional.class));
        }
    }

    @SuppressWarnings("unused") // used via @MethodSource (referenced by String)
    static Stream<Arguments> addressLookupFailures() {
        return Stream.of(
                Arguments.of(
                        "postal code is not found",
                        new PostalCodeNotFoundException(
                                ApiErrorMessages.POSTAL_CODE_NOT_FOUND
                        )
                ),
                Arguments.of(
                        "address provider is unavailable",
                        new AddressProviderUnavailableException(
                                ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE
                        )
                )
        );
    }

    @SuppressWarnings("unused") // used via @MethodSource (referenced by String)
    static Stream<Arguments> partialUpdates() {
        return Stream.of(
                Arguments.of(
                        "only name is sent",
                        new ClientUpdateRequestDTO(UPDATED_NAME, null, null, null),
                        new ClientUpdateData(UPDATED_NAME, null, null, null)
                ),
                Arguments.of(
                        "only email is sent",
                        new ClientUpdateRequestDTO(null, UPDATED_EMAIL, null, null),
                        new ClientUpdateData(null, UPDATED_EMAIL, null, null)
                ),
                Arguments.of(
                        "only phone number is sent",
                        new ClientUpdateRequestDTO(null, null, UPDATED_PHONE, null),
                        new ClientUpdateData(null, null, UPDATED_PHONE, null)
                ),
                Arguments.of(
                        "nothing is sent",
                        new ClientUpdateRequestDTO(null, null, null, null),
                        new ClientUpdateData(null, null, null, null)
                )
        );
    }

    @SuppressWarnings("unused") // used via @MethodSource (referenced by String)
    static Stream<Arguments> addressForwarding() {
        return Stream.of(
                Arguments.of(
                        "street number and complement are sent",
                        new AddressUpdateRequestDTO("456", "Apto 22", POSTAL_CODE),
                        "456",
                        "Apto 22"
                ),
                Arguments.of(
                        "complement is null",
                        new AddressUpdateRequestDTO("456", null, POSTAL_CODE),
                        "456",
                        null
                ),
                Arguments.of(
                        "blank complement is forwarded untouched",
                        new AddressUpdateRequestDTO("456", "   ", POSTAL_CODE),
                        "456",
                        "   "
                )
        );
    }

    private void stubClientLookup(Client client) {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(
                clientRepository,
                clientPersistenceService,
                addressService,
                currentUserService
        );
    }

    private static void assertThrowsWithMessage(
            Class<? extends Exception> type,
            String message,
            Executable executable
    ) {
        Exception exception = assertThrows(type, executable);
        assertEquals(message, exception.getMessage());
    }

    private ClientRequestDTO clientRequest() {
        Address address = baseClient.getAddress();

        return new ClientRequestDTO(
                baseClient.getName(),
                baseClient.getCpf(),
                baseClient.getEmail(),
                baseClient.getPhoneNumber(),
                new AddressRequestDTO(
                        address.getStreetNumber(),
                        address.getComplement(),
                        address.getPostalCode()
                )
        );
    }

    private static ClientUpdateRequestDTO fullUpdateRequest() {
        return new ClientUpdateRequestDTO(
                UPDATED_NAME,
                UPDATED_EMAIL,
                UPDATED_PHONE,
                new AddressUpdateRequestDTO(
                        "456",
                        "Apto 22",
                        "87654321"
                )
        );
    }

    private static AddressData addressData() {
        return new AddressData(
                "Rua Atualizada",
                "Centro",
                "São Paulo",
                State.SP,
                "87654321"
        );
    }

    private static String maskedCpf(String cpf) {
        return "***.***.***-" + cpf.substring(9);
    }

    private static ClientSummaryResponseDTO clientSummaryResponse(Client client) {
        return new ClientSummaryResponseDTO(
                client.getId(),
                client.getName(),
                maskedCpf(client.getCpf()),
                client.getEmail(),
                client.getPhoneNumber()
        );
    }

    private static ClientResponseDTO clientResponse(Client client) {
        return new ClientResponseDTO(
                client.getId(),
                client.getName(),
                maskedCpf(client.getCpf()),
                client.getEmail(),
                client.getPhoneNumber(),
                AddressResponseDTO.fromEntity(client.getAddress())
        );
    }
}
