package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.AddressResponseDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.dto.response.ClientSummaryResponseDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.AddressService;
import com.lucas.bankingsystem.service.ClientService;
import com.lucas.bankingsystem.service.address.AddressData;
import com.lucas.bankingsystem.service.security.CurrentUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {

    private static final String PROVIDER = "com.lucas.bankingsystem.service.client.ClientServiceTest#";

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

    private static final String CPF_ALREADY_EXISTS_MESSAGE = "Já existe um cliente cadastrado com este CPF.";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Cliente não encontrado.";

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

    @Nested
    @DisplayName("create(ClientRequestDTO)")
    class Create {

        @Test
        @DisplayName("Should look up the address before calling the persistence service")
        void shouldCreateClientSuccessfully() {
            ClientRequestDTO request = clientRequest();
            Address address = address();
            Client savedClient = client();

            when(clientRepository.existsByCpf(CPF)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(request, address, USERNAME))
                    .thenReturn(savedClient);

            Client result = clientService.create(request);

            assertSame(savedClient, result);

            InOrder inOrder = inOrder(
                    clientRepository,
                    addressService,
                    currentUserService,
                    clientPersistenceService
            );
            inOrder.verify(clientRepository).existsByCpf(CPF);
            inOrder.verify(addressService).createFromPostalCode(request.address());
            inOrder.verify(currentUserService).getUsername();
            inOrder.verify(clientPersistenceService).create(request, address, USERNAME);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when CPF already exists")
        void shouldThrowWhenCpfAlreadyExists() {
            ClientRequestDTO request = clientRequest();

            when(clientRepository.existsByCpf(CPF)).thenReturn(true);

            assertThrowsWithMessage(
                    ClientCpfAlreadyExistsException.class,
                    CPF_ALREADY_EXISTS_MESSAGE,
                    () -> clientService.create(request)
            );

            verify(clientRepository).existsByCpf(CPF);
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

            when(clientRepository.existsByCpf(CPF)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address()))
                    .thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.create(request)
            );

            assertSame(exception, thrown);

            verify(clientRepository).existsByCpf(CPF);
            verify(addressService).createFromPostalCode(request.address());
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should propagate the failure when persistence fails")
        void shouldPropagateFailureWhenPersistenceFails() {
            ClientRequestDTO request = clientRequest();
            Address address = address();
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("uk_client_cpf");

            when(clientRepository.existsByCpf(CPF)).thenReturn(false);
            when(addressService.createFromPostalCode(request.address())).thenReturn(address);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.create(request, address, USERNAME))
                    .thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.create(request)
            );

            assertSame(exception, thrown);

            verify(clientRepository).existsByCpf(CPF);
            verify(addressService).createFromPostalCode(request.address());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).create(request, address, USERNAME);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("Should return all clients successfully")
        void shouldReturnAllClientsSuccessfully() {
            Client client1 = client();
            Client client2 = client(
                    2L,
                    "Maria Silva",
                    "91741354064",
                    "maria@email.com",
                    "11988888888"
            );

            when(clientRepository.findAll()).thenReturn(List.of(client1, client2));

            List<ClientSummaryResponseDTO> result = clientService.findAll();

            assertEquals(
                    List.of(
                            clientSummaryResponse(),
                            new ClientSummaryResponseDTO(
                                    2L,
                                    "Maria Silva",
                                    "91741354064",
                                    "maria@email.com",
                                    "11988888888"
                            )
                    ),
                    result
            );

            verify(clientRepository).findAll();
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should return empty list when there are no clients")
        void shouldReturnEmptyListWhenThereAreNoClients() {
            when(clientRepository.findAll()).thenReturn(List.of());

            List<ClientSummaryResponseDTO> result = clientService.findAll();

            assertEquals(List.of(), result);

            verify(clientRepository).findAll();
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("findEntityById(Long)")
    class FindEntityById {

        @Test
        @DisplayName("Should return the client entity when ID exists")
        void shouldReturnClientEntityWhenIdExists() {
            Client client = client();

            stubClientLookup(client);

            Client result = clientService.findEntityById(CLIENT_ID);

            assertSame(client, result);

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    CLIENT_NOT_FOUND_MESSAGE,
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
            stubClientLookup(client());

            ClientResponseDTO result = clientService.findById(CLIENT_ID);

            assertEquals(clientResponse(), result);

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    CLIENT_NOT_FOUND_MESSAGE,
                    () -> clientService.findById(CLIENT_ID)
            );

            verify(clientRepository).findById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("update(Long, ClientUpdateRequestDTO)")
    class Update {

        @Test
        @DisplayName("Should look up the address before calling the persistence service")
        void shouldUpdateClientAndAddressSuccessfully() {
            Client client = client();
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();

            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenReturn(addressData);
            when(clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            )).thenReturn(client);

            Client result = clientService.update(CLIENT_ID, request);

            assertSame(client, result);

            InOrder inOrder = inOrder(
                    currentUserService,
                    addressService,
                    clientPersistenceService
            );
            inOrder.verify(currentUserService).getUsername();
            inOrder.verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            inOrder.verify(clientPersistenceService).update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            );
            verifyNoMoreInteractionsOnMocks();
        }

        // Todos os cenários têm address = null:
        // o serviço não deve consultar o endereço e deve repassar addressData = null.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "partialUpdates")
        @DisplayName("Should forward updates without address and without looking it up")
        void shouldForwardUpdatesWithoutLookingUpTheAddress(
                String scenario,
                ClientUpdateRequestDTO request
        ) {
            Client client = client();

            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            )).thenReturn(client);

            Client result = clientService.update(CLIENT_ID, request);

            assertSame(client, result);

            verify(currentUserService).getUsername();
            verify(clientPersistenceService).update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            );
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

            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.update(CLIENT_ID, request)
            );

            assertSame(exception, thrown);

            verify(currentUserService).getUsername();
            verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should propagate the failure when persistence fails")
        void shouldPropagateFailureWhenPersistenceFails() {
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressData addressData = addressData();
            ClientNotFoundException exception =
                    new ClientNotFoundException(CLIENT_NOT_FOUND_MESSAGE);

            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenReturn(addressData);
            when(clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            )).thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.update(CLIENT_ID, request)
            );

            assertSame(exception, thrown);

            verify(currentUserService).getUsername();
            verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            verify(clientPersistenceService).update(
                    CLIENT_ID,
                    request,
                    addressData,
                    USERNAME
            );
            verifyNoMoreInteractionsOnMocks();
        }
    }

    @Nested
    @DisplayName("transaction boundaries")
    class TransactionBoundaries {

        // A transação de escrita pertence ao ClientPersistenceService.
        // Se create/update (ou a classe) voltarem a ser @Transactional aqui,
        // a conexão passa a ficar aberta durante a consulta de CEP.
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

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> addressLookupFailures() {
        return Stream.of(
                Arguments.of(
                        "postal code is not found",
                        new PostalCodeNotFoundException(
                                "CEP 01001000 não encontrado em nenhum provedor."
                        )
                ),
                Arguments.of(
                        "address provider is unavailable",
                        new AddressProviderUnavailableException(
                                "Serviços de CEP indisponíveis no momento."
                        )
                )
        );
    }

    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> partialUpdates() {
        return Stream.of(
                Arguments.of(
                        "only name is sent",
                        new ClientUpdateRequestDTO(UPDATED_NAME, null, null, null)
                ),
                Arguments.of(
                        "only email is sent",
                        new ClientUpdateRequestDTO(null, UPDATED_EMAIL, null, null)
                ),
                Arguments.of(
                        "only phone number is sent",
                        new ClientUpdateRequestDTO(null, null, UPDATED_PHONE, null)
                ),
                Arguments.of(
                        "nothing is sent",
                        new ClientUpdateRequestDTO(null, null, null, null)
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

    private static ClientRequestDTO clientRequest() {
        return new ClientRequestDTO(
                NAME,
                CPF,
                EMAIL,
                PHONE,
                new AddressRequestDTO("123", null, POSTAL_CODE)
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

    private static Address address() {
        return new Address(
                "Rua Teste",
                "123",
                null,
                "Centro",
                "São Paulo",
                State.SP,
                POSTAL_CODE
        );
    }

    private static Client client() {
        return client(CLIENT_ID, NAME, CPF, EMAIL, PHONE);
    }

    private static Client client(
            Long id,
            String name,
            String cpf,
            String email,
            String phoneNumber
    ) {
        Client client = new Client(name, cpf, email, phoneNumber, address());
        ReflectionTestUtils.setField(client, "id", id);
        return client;
    }

    private static ClientSummaryResponseDTO clientSummaryResponse() {
        return new ClientSummaryResponseDTO(
                CLIENT_ID,
                NAME,
                CPF,
                EMAIL,
                PHONE
        );
    }

    private static ClientResponseDTO clientResponse() {
        return new ClientResponseDTO(
                CLIENT_ID,
                NAME,
                CPF,
                EMAIL,
                PHONE,
                AddressResponseDTO.fromEntity(address())
        );
    }
}