package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
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
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.AddressService;
import com.lucas.bankingsystem.service.ClientService;
import com.lucas.bankingsystem.service.security.CurrentUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        @DisplayName("Should create client successfully")
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

            verify(clientRepository).existsByCpf(CPF);
            verify(addressService).createFromPostalCode(request.address());
            verify(currentUserService).getUsername();
            verify(clientPersistenceService).create(request, address, USERNAME);
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
        @DisplayName("Should propagate the failure and save nothing when address lookup fails")
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
        @DisplayName("Should update client and address successfully")
        void shouldUpdateClientAndAddressSuccessfully() {
            Client client = client();
            ClientUpdateRequestDTO request = fullUpdateRequest();
            AddressLookupResponse addressData = addressLookupResponse();

            when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
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

            verify(clientRepository).existsById(CLIENT_ID);
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

        // Todos os cenários têm address = null:
        // o serviço não deve consultar o endereço.
        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "partialUpdates")
        @DisplayName("Should forward partial updates without looking up the address")
        void shouldUpdateOnlyTheProvidedFields(
                String scenario,
                ClientUpdateRequestDTO request
        ) {
            Client client = client();

            when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(clientPersistenceService.update(
                    CLIENT_ID,
                    request,
                    null,
                    USERNAME
            )).thenReturn(client);

            Client result = clientService.update(CLIENT_ID, request);

            assertSame(client, result);

            verify(clientRepository).existsById(CLIENT_ID);
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

            when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
            when(currentUserService.getUsername()).thenReturn(USERNAME);
            when(addressService.findAddressByPostalCode(
                    request.address().postalCode()
            )).thenThrow(exception);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> clientService.update(CLIENT_ID, request)
            );

            assertSame(exception, thrown);

            verify(clientRepository).existsById(CLIENT_ID);
            verify(currentUserService).getUsername();
            verify(addressService).findAddressByPostalCode(
                    request.address().postalCode()
            );
            verifyNoMoreInteractionsOnMocks();
        }

        @Test
        @DisplayName("Should throw when client does not exist")
        void shouldThrowWhenClientDoesNotExist() {
            ClientUpdateRequestDTO request = fullUpdateRequest();

            when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

            assertThrowsWithMessage(
                    ClientNotFoundException.class,
                    CLIENT_NOT_FOUND_MESSAGE,
                    () -> clientService.update(CLIENT_ID, request)
            );

            verify(clientRepository).existsById(CLIENT_ID);
            verifyNoMoreInteractionsOnMocks();
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
                new com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO(
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
        org.springframework.test.util.ReflectionTestUtils.setField(client, "id", id);
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