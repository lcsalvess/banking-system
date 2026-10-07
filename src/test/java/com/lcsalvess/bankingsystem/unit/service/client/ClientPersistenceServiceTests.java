package com.lcsalvess.bankingsystem.unit.service.client;

import com.lcsalvess.bankingsystem.entity.Address;
import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.event.client.ClientOperationEvent;
import com.lcsalvess.bankingsystem.event.client.ClientOperationType;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.service.address.AddressData;
import com.lcsalvess.bankingsystem.service.client.ClientPersistenceService;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
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
class ClientPersistenceServiceTests {

    private static final String CREATE_TIMER_NAME = "client.persistence.create";
    private static final String UPDATE_TIMER_NAME = "client.persistence.update";

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.service.client.ClientPersistenceServiceTests#";

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
    @DisplayName("create(Client, String)")
    class Create {

        @Test
        @DisplayName("Should save the received client and return the saved entity")
        void shouldCreateClientSuccessfully() {
            Client client = unsavedClient();

            when(clientRepository.save(client)).thenAnswer(invocation -> {
                Client toSave = invocation.getArgument(0);
                setClientId(toSave);
                return toSave;
            });

            Client result = clientPersistenceService.create(client, USERNAME);

            assertSame(client, result);
            assertEquals(CLIENT_ID, result.getId());

            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should publish client created event")
        void shouldPublishClientCreatedEvent() {
            Client client = unsavedClient();

            when(clientRepository.save(client)).thenAnswer(invocation -> {
                Client toSave = invocation.getArgument(0);
                setClientId(toSave);
                return toSave;
            });

            clientPersistenceService.create(client, USERNAME);

            ClientOperationEvent event = capturePublishedEvent();

            assertEquals(CLIENT_ID, event.clientId());
            assertEquals(ClientOperationType.CREATED, event.type());
            assertEquals(USERNAME, event.username());

            verify(clientRepository).save(client);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should record the persistence timer")
        void shouldRecordPersistenceTimer() {
            Client client = unsavedClient();

            when(clientRepository.save(client)).thenReturn(client);

            clientPersistenceService.create(client, USERNAME);

            assertEquals(1, meterRegistry.get(CREATE_TIMER_NAME).timer().count());

            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should propagate the failure and not publish the event when save fails")
        void shouldNotPublishEventWhenSaveFails() {
            Client client = unsavedClient();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("uk_client_cpf");

            when(clientRepository.save(client)).thenThrow(exception);

            DataIntegrityViolationException thrown = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> clientPersistenceService.create(client, USERNAME)
            );

            assertSame(exception, thrown);

            verify(clientRepository).save(client);
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }
    }

    @Nested
    @DisplayName("update(Long, ClientUpdateData, String)")
    class Update {

        @Test
        @DisplayName("Should update client fields and keep the address when none is sent")
        void shouldUpdateClientSuccessfully() {
            Client client = client();
            Address originalAddress = client.getAddress();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    clientOnlyUpdateData(),
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
                ClientUpdateData data,
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
                    data,
                    USERNAME
            );

            assertEquals(expectedName, result.getName());
            assertEquals(expectedEmail, result.getEmail());
            assertEquals(expectedPhone, result.getPhoneNumber());
            assertAddressUnchanged(result.getAddress());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should update client address successfully")
        void shouldUpdateClientAddressSuccessfully() {
            Client client = client();
            ClientUpdateData data = new ClientUpdateData(
                    UPDATED_NAME,
                    UPDATED_EMAIL,
                    UPDATED_PHONE,
                    new AddressUpdateData(addressData(), "456", "Apto 22")
            );

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    data,
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
        @DisplayName("Should update address fields according to the update data")
        void shouldUpdateAddressFields(
                String scenario,
                String streetNumber,
                String complement,
                String expectedStreetNumber,
                String expectedComplement
        ) {
            Client client = client();
            ClientUpdateData data = new ClientUpdateData(
                    null,
                    null,
                    null,
                    new AddressUpdateData(addressData(), streetNumber, complement)
            );

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    data,
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
            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.empty());

            assertClientNotFound(
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            fullUpdateData(),
                            USERNAME
                    )
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Should propagate the failure and not publish the event when save fails")
        void shouldNotPublishEventWhenSaveFails() {
            Client client = client();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("update failed");

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenThrow(exception);

            DataIntegrityViolationException thrown = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            clientOnlyUpdateData(),
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
        @DisplayName("Should record the update timer")
        void shouldRecordUpdateTimer() {
            Client client = client();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            clientPersistenceService.update(
                    CLIENT_ID,
                    clientOnlyUpdateData(),
                    USERNAME
            );

            assertEquals(1, meterRegistry.get(UPDATE_TIMER_NAME).timer().count());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should publish client updated event")
        void shouldPublishClientUpdatedEvent() {
            Client client = client();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            clientPersistenceService.update(
                    CLIENT_ID,
                    clientOnlyUpdateData(),
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
                    Client.class,
                    String.class
            );
            Method update = ClientPersistenceService.class.getMethod(
                    "update",
                    Long.class,
                    ClientUpdateData.class,
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
                        new ClientUpdateData(UPDATED_NAME, null, null, null),
                        UPDATED_NAME,
                        EMAIL,
                        PHONE
                ),
                Arguments.of(
                        "only email is sent",
                        new ClientUpdateData(null, UPDATED_EMAIL, null, null),
                        NAME,
                        UPDATED_EMAIL,
                        PHONE
                ),
                Arguments.of(
                        "only phone number is sent",
                        new ClientUpdateData(null, null, UPDATED_PHONE, null),
                        NAME,
                        EMAIL,
                        UPDATED_PHONE
                ),
                Arguments.of(
                        "nothing is sent",
                        new ClientUpdateData(null, null, null, null),
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
                        "456",
                        "Apto 22",
                        "456",
                        "Apto 22"
                ),
                Arguments.of(
                        "complement is cleared when blank",
                        "456",
                        "   ",
                        "456",
                        null
                ),
                Arguments.of(
                        "complement is cleared when null",
                        "456",
                        null,
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

    // Atualização sem endereço: nesse caso o ClientService repassa address = null.
    private static ClientUpdateData clientOnlyUpdateData() {
        return new ClientUpdateData(
                UPDATED_NAME,
                UPDATED_EMAIL,
                UPDATED_PHONE,
                null
        );
    }

    // Atualização com endereço: o ClientService repassa o endereço consultado.
    private static ClientUpdateData fullUpdateData() {
        return new ClientUpdateData(
                UPDATED_NAME,
                UPDATED_EMAIL,
                UPDATED_PHONE,
                new AddressUpdateData(addressData(), "456", "Apto 22")
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

    // Cliente ainda não persistido (sem id), como chega do ClientService na criação.
    private static Client unsavedClient() {
        return new Client(NAME, CPF, EMAIL, PHONE, address());
    }

    private static Client client() {
        Client client = unsavedClient();
        setClientId(client);
        return client;
    }

    private static void setClientId(Client client) {
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
    }
}