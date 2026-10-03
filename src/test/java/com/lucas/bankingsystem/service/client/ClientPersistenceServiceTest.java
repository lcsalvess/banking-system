package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.address.AddressData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientPersistenceService tests")
class ClientPersistenceServiceTest {

    private static final String PROVIDER =
            "com.lucas.bankingsystem.service.client.ClientPersistenceServiceTest#";

    private static final String USERNAME = "usuario.teste";

    private static final Long CLIENT_ID = 1L;
    private static final String NAME = "Cliente Teste";
    private static final String CPF = "52998224725";
    private static final String EMAIL = "teste@email.com";
    private static final String PHONE = "11999999999";
    private static final String POSTAL_CODE = "01001000";

    private static final String UPDATED_NAME = "Cliente Atualizado";
    private static final String UPDATED_EMAIL = "atualizado@email.com";
    private static final String UPDATED_PHONE = "11888888888";

    private static final String CLIENT_NOT_FOUND_MESSAGE =
            "Cliente não encontrado.";

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ClientPersistenceService clientPersistenceService;

    @BeforeEach
    void setUp() {
        clientPersistenceService = new ClientPersistenceService(
                clientRepository,
                eventPublisher,
                meterRegistry
        );
    }

    @Nested
    @DisplayName("create(ClientRequestDTO, Address, String)")
    class Create {

        @Test
        @DisplayName("Should create client successfully")
        void shouldCreateClientSuccessfully() {
            ClientRequestDTO request = clientRequest();
            Address address = address();

            when(clientRepository.save(any(Client.class)))
                    .thenAnswer(invocation -> {
                        Client client = invocation.getArgument(0);
                        setClientId(client);
                        return client;
                    });

            Client result = clientPersistenceService.create(
                    request,
                    address,
                    USERNAME
            );

            assertEquals(CLIENT_ID, result.getId());
            assertEquals(NAME, result.getName());
            assertEquals(CPF, result.getCpf());
            assertEquals(EMAIL, result.getEmail());
            assertEquals(PHONE, result.getPhoneNumber());
            assertSame(address, result.getAddress());

            verify(clientRepository).save(any(Client.class));
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should save client with the provided address")
        void shouldSaveClientWithProvidedAddress() {
            ClientRequestDTO request = clientRequest();
            Address address = address();

            when(clientRepository.save(any(Client.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            clientPersistenceService.create(request, address, USERNAME);

            ArgumentCaptor<Client> clientCaptor =
                    ArgumentCaptor.forClass(Client.class);

            verify(clientRepository).save(clientCaptor.capture());

            Client savedClient = clientCaptor.getValue();

            assertEquals(NAME, savedClient.getName());
            assertEquals(CPF, savedClient.getCpf());
            assertEquals(EMAIL, savedClient.getEmail());
            assertEquals(PHONE, savedClient.getPhoneNumber());
            assertSame(address, savedClient.getAddress());

            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should publish client created event")
        void shouldPublishClientCreatedEvent() {
            ClientRequestDTO request = clientRequest();

            when(clientRepository.save(any(Client.class)))
                    .thenAnswer(invocation -> {
                        Client client = invocation.getArgument(0);
                        setClientId(client);
                        return client;
                    });

            clientPersistenceService.create(request, address(), USERNAME);

            ClientOperationEvent event = capturePublishedEvent();

            assertEquals(CLIENT_ID, event.clientId());
            assertEquals(ClientOperationType.CREATED, event.type());
            assertEquals(USERNAME, event.username());

            verify(clientRepository).save(any(Client.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should propagate the failure and not publish the event when save fails")
        void shouldNotPublishEventWhenSaveFails() {
            ClientRequestDTO request = clientRequest();
            Address address = address();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("uk_client_cpf");

            when(clientRepository.save(any(Client.class))).thenThrow(exception);

            DataIntegrityViolationException thrown = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> clientPersistenceService.create(request, address, USERNAME)
            );

            assertSame(exception, thrown);

            verify(clientRepository).save(any(Client.class));
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }
    }

    @Nested
    @DisplayName("update(Long, ClientUpdateRequestDTO, AddressLookupResponse, String)")
    class Update {

        @Test
        @DisplayName("Should update client fields and keep the address when none is sent")
        void shouldUpdateClientSuccessfully() {
            Client client = client();
            Address originalAddress = client.getAddress();
            ClientUpdateRequestDTO request = clientOnlyUpdateRequest();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            );

            assertSame(client, result);
            assertEquals(UPDATED_NAME, result.getName());
            assertEquals(UPDATED_EMAIL, result.getEmail());
            assertEquals(UPDATED_PHONE, result.getPhoneNumber());
            assertSame(originalAddress, result.getAddress());
            assertAddressUnchanged(result.getAddress());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "partialUpdates")
        @DisplayName("Should update only the provided client fields")
        void shouldUpdateOnlyTheProvidedFields(
                String scenario,
                ClientUpdateRequestDTO request,
                String expectedName,
                String expectedEmail,
                String expectedPhone
        ) {
            Client client = client();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            );

            assertEquals(expectedName, result.getName());
            assertEquals(expectedEmail, result.getEmail());
            assertEquals(expectedPhone, result.getPhoneNumber());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should update client address successfully")
        void shouldUpdateClientAddressSuccessfully() {
            Client client = client();
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            );

            Address updatedAddress = result.getAddress();

            assertEquals("Rua Atualizada", updatedAddress.getStreetName());
            assertEquals("Centro", updatedAddress.getNeighborhood());
            assertEquals("São Paulo", updatedAddress.getCity());
            assertEquals(State.SP, updatedAddress.getState());
            assertEquals("87654321", updatedAddress.getPostalCode());
            assertEquals("456", updatedAddress.getStreetNumber());
            assertEquals("Apto 22", updatedAddress.getComplement());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "addressUpdates")
        @DisplayName("Should update address fields according to the request")
        void shouldUpdateAddressFields(
                String scenario,
                AddressUpdateRequestDTO addressRequest,
                String expectedStreetNumber,
                String expectedComplement
        ) {
            Client client = client();
            AddressData addressData = addressData();

            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(
                    null,
                    null,
                    null,
                    addressRequest
            );

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            );

            Address updatedAddress = result.getAddress();

            assertEquals(expectedStreetNumber, updatedAddress.getStreetNumber());
            assertEquals(expectedComplement, updatedAddress.getComplement());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.empty());

            assertClientNotFound(
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            request,
                            addressData,
                            USERNAME
                    )
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Should reject an address update without the looked-up address data")
        void shouldRejectAddressUpdateWithoutLookupData() {
            ClientUpdateRequestDTO request = fullUpdateRequest();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            request,
                            null,
                            USERNAME
                    )
            );

            verifyNoInteractions(clientRepository, eventPublisher);
        }

        @Test
        @DisplayName("Should propagate the failure and not publish the event when save fails")
        void shouldNotPublishEventWhenSaveFails() {
            Client client = client();
            ClientUpdateRequestDTO request = clientOnlyUpdateRequest();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("update failed");

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenThrow(exception);

            DataIntegrityViolationException thrown = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            request,
                            null,
                            USERNAME
                    )
            );

            assertSame(exception, thrown);

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Should publish client updated event")
        void shouldPublishClientUpdatedEvent() {
            Client client = client();
            ClientUpdateRequestDTO request = clientOnlyUpdateRequest();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            );

            ClientOperationEvent event = capturePublishedEvent();

            assertEquals(CLIENT_ID, event.clientId());
            assertEquals(ClientOperationType.UPDATED, event.type());
            assertEquals(USERNAME, event.username());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transaction boundaries")
    class TransactionBoundaries {

        // Este serviço é o dono da transação de escrita. A anotação precisa ser
        // a do Spring: o proxy é criado via @Transactional e o evento
        // AFTER_COMMIT depende dessa transação existir no momento do publish.
        @Test
        @DisplayName("create and update should run inside a transaction")
        void createAndUpdateShouldBeTransactional() throws NoSuchMethodException {
            Method create = ClientPersistenceService.class.getMethod(
                    "create",
                    ClientRequestDTO.class,
                    Address.class,
                    String.class
            );
            Method update = ClientPersistenceService.class.getMethod(
                    "update",
                    Long.class,
                    ClientUpdateRequestDTO.class,
                    AddressData.class,
                    String.class
            );

            assertTrue(AnnotatedElementUtils.hasAnnotation(create, Transactional.class));
            assertTrue(AnnotatedElementUtils.hasAnnotation(update, Transactional.class));
        }
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> partialUpdates() {
        return Stream.of(
                Arguments.of(
                        "only name is sent",
                        new ClientUpdateRequestDTO(UPDATED_NAME, null, null, null),
                        UPDATED_NAME,
                        EMAIL,
                        PHONE
                ),
                Arguments.of(
                        "only email is sent",
                        new ClientUpdateRequestDTO(null, UPDATED_EMAIL, null, null),
                        NAME,
                        UPDATED_EMAIL,
                        PHONE
                ),
                Arguments.of(
                        "only phone number is sent",
                        new ClientUpdateRequestDTO(null, null, UPDATED_PHONE, null),
                        NAME,
                        EMAIL,
                        UPDATED_PHONE
                ),
                Arguments.of(
                        "nothing is sent",
                        new ClientUpdateRequestDTO(null, null, null, null),
                        NAME,
                        EMAIL,
                        PHONE
                )
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> addressUpdates() {
        return Stream.of(
                Arguments.of(
                        "street number and complement are updated",
                        new AddressUpdateRequestDTO("456", "Apto 22", POSTAL_CODE),
                        "456",
                        "Apto 22"
                ),
                Arguments.of(
                        "complement is cleared when blank",
                        new AddressUpdateRequestDTO("456", "   ", POSTAL_CODE),
                        "456",
                        null
                ),
                Arguments.of(
                        "complement is cleared when null",
                        new AddressUpdateRequestDTO("456", null, POSTAL_CODE),
                        "456",
                        null
                )
        );
    }

    private ClientOperationEvent capturePublishedEvent() {
        ArgumentCaptor<ClientOperationEvent> eventCaptor =
                ArgumentCaptor.forClass(ClientOperationEvent.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        return eventCaptor.getValue();
    }

    private void verifyNoMoreInteractionsOnMocks() {
        verifyNoMoreInteractions(clientRepository, eventPublisher);
    }

    private static void assertClientNotFound(Executable executable) {
        ClientNotFoundException exception = assertThrows(
                ClientNotFoundException.class,
                executable
        );

        assertEquals(CLIENT_NOT_FOUND_MESSAGE, exception.getMessage());
    }

    private static void assertAddressUnchanged(Address address) {
        assertEquals("Rua Teste", address.getStreetName());
        assertEquals("123", address.getStreetNumber());
        assertEquals("Apto 10", address.getComplement());
        assertEquals("Centro", address.getNeighborhood());
        assertEquals("São Paulo", address.getCity());
        assertEquals(State.SP, address.getState());
        assertEquals(POSTAL_CODE, address.getPostalCode());
    }

    private static ClientRequestDTO clientRequest() {
        return new ClientRequestDTO(
                NAME,
                CPF,
                EMAIL,
                PHONE,
                new AddressRequestDTO("123", null, POSTAL_CODE)
        );
    }

    // Atualização sem endereço: nesse caso o ClientService repassa addressData = null.
    private static ClientUpdateRequestDTO clientOnlyUpdateRequest() {
        return new ClientUpdateRequestDTO(
                UPDATED_NAME,
                UPDATED_EMAIL,
                UPDATED_PHONE,
                null
        );
    }

    // Atualização com endereço: o ClientService repassa o addressData consultado.
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

    private static Address address() {
        return new Address(
                "Rua Teste",
                "123",
                "Apto 10",
                "Centro",
                "São Paulo",
                State.SP,
                POSTAL_CODE
        );
    }

    private static Client client() {
        Client client = new Client(NAME, CPF, EMAIL, PHONE, address());
        setClientId(client);
        return client;
    }

    private static void setClientId(Client client) {
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
    }
}