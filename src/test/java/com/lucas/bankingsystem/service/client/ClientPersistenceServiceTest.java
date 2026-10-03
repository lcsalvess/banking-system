package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.repository.ClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    @InjectMocks
    private ClientPersistenceService clientPersistenceService;

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

            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("update(Long, ClientUpdateRequestDTO, AddressLookupResponse, String)")
    class Update {

        @Test
        @DisplayName("Should update client successfully")
        void shouldUpdateClientSuccessfully() {
            Client client = client();
            ClientUpdateRequestDTO request = fullUpdateRequest();

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
            AddressLookupResponse addressData = addressLookupResponse();

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
            AddressLookupResponse addressData = addressLookupResponse();

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
        @DisplayName("Should preserve existing address when address data is null")
        void shouldPreserveExistingAddressWhenAddressDataIsNull() {
            Client client = client();
            Address originalAddress = client.getAddress();

            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(
                    UPDATED_NAME,
                    null,
                    null,
                    null
            );

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.of(client));
            when(clientRepository.save(client)).thenReturn(client);

            Client result = clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            );

            assertSame(originalAddress, result.getAddress());
            assertEquals("Rua Teste", result.getAddress().getStreetName());
            assertEquals("123", result.getAddress().getStreetNumber());
            assertEquals("Centro", result.getAddress().getNeighborhood());
            assertEquals("São Paulo", result.getAddress().getCity());
            assertEquals(State.SP, result.getAddress().getState());
            assertEquals(POSTAL_CODE, result.getAddress().getPostalCode());

            verify(clientRepository).findById(CLIENT_ID);
            verify(clientRepository).save(client);
            verify(eventPublisher).publishEvent(any(ClientOperationEvent.class));
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            ClientUpdateRequestDTO request = fullUpdateRequest();

            when(clientRepository.findById(CLIENT_ID))
                    .thenReturn(Optional.empty());

            assertClientNotFound(
                    () -> clientPersistenceService.update(
                            CLIENT_ID,
                            request,
                            null,
                            USERNAME
                    )
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoInteractions(eventPublisher);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Should publish client updated event")
        void shouldPublishClientUpdatedEvent() {
            Client client = client();
            ClientUpdateRequestDTO request = fullUpdateRequest();

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

    private static ClientRequestDTO clientRequest() {
        return new ClientRequestDTO(
                NAME,
                CPF,
                EMAIL,
                PHONE,
                new com.lucas.bankingsystem.dto.request.AddressRequestDTO(
                        "123",
                        null,
                        POSTAL_CODE
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

    private static AddressLookupResponse addressLookupResponse() {
        return new AddressLookupResponse(
                "Rua Atualizada",
                "Centro",
                "São Paulo",
                "SP",
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
        org.springframework.test.util.ReflectionTestUtils.setField(
                client,
                "id",
                ClientPersistenceServiceTest.CLIENT_ID
        );
    }
}