package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AddressService addressService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClientService clientService;

    private ClientRequestDTO createClientRequestDTO() {
        AddressRequestDTO address = new AddressRequestDTO(
                "123",
                null,
                "12345678"
        );

        return new ClientRequestDTO(
                "Cliente Teste",
                "12345678901",
                "teste@email.com",
                "11999999999",
                address
        );
    }

    private Address createAddress() {
        return new Address(
                "Rua Teste",
                "123",
                null,
                "Centro",
                "São Paulo",
                State.SP,
                "01001000"
        );
    }

    private Client createClient() {
        return new Client(
                "Cliente Teste",
                "12345678901",
                "teste@email.com",
                "11999999999",
                createAddress()
        );
    }

    @Nested
    @DisplayName("Ao salvar um cliente")
    class SaveTests {

        @Test
        @DisplayName("Deve lançar exceção ao tentar salvar cliente com CPF já cadastrado.")
        void shouldThrowExceptionWhenCpfAlreadyExists() {
            ClientRequestDTO dto = createClientRequestDTO();

            when(clientRepository.existsByCpf(dto.cpf())).thenReturn(true);

            assertThrows(
                    ClientCpfAlreadyExistsException.class,
                    () -> clientService.create(dto)
            );

            verify(clientRepository).existsByCpf(dto.cpf());
            verify(clientRepository, never()).save(any(Client.class));
            verifyNoInteractions(addressService);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve salvar cliente com sucesso quando CPF estiver livre.")
        void shouldSaveClientSuccessfully() {
            ClientRequestDTO dto = createClientRequestDTO();

            when(clientRepository.existsByCpf(dto.cpf())).thenReturn(false);

            when(addressService.createFromPostalCode(dto.address()))
                    .thenReturn(createAddress());

            when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
                Client savedClient = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedClient, "id", 1L);
                return savedClient;
            });

            Client savedClient = clientService.create(dto);

            assertNotNull(savedClient);
            assertEquals(1L, savedClient.getId());
            assertEquals(dto.cpf(), savedClient.getCpf());
            assertEquals(dto.name(), savedClient.getName());

            verify(clientRepository).existsByCpf(dto.cpf());
            verify(addressService).createFromPostalCode(dto.address());
            verify(clientRepository).save(any(Client.class));
            verifyNoMoreInteractions(clientRepository, addressService);

            verify(eventPublisher).publishEvent(
                    new ClientOperationEvent(1L, ClientOperationType.CREATED)
            );
        }
    }

    @Nested
    @DisplayName("Ao listar todos os clientes")
    class FindAllTests {

        @Test
        @DisplayName("Deve retornar lista de DTOs quando existirem clientes cadastrados.")
        void shouldReturnListOfClientsWhenRecordsExist() {
            Client client = createClient();
            ReflectionTestUtils.setField(client, "id", 1L);

            when(clientRepository.findAll()).thenReturn(List.of(client));

            List<ClientResponseDTO> result = clientService.findAll();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(1L, result.getFirst().id());
            assertEquals(client.getName(), result.getFirst().name());
            assertEquals(client.getCpf(), result.getFirst().cpf());

            verify(clientRepository).findAll();
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando não existirem clientes cadastrados.")
        void shouldReturnEmptyListWhenNoRecordsExist() {
            when(clientRepository.findAll()).thenReturn(List.of());

            List<ClientResponseDTO> result = clientService.findAll();

            assertNotNull(result);
            assertTrue(result.isEmpty());

            verify(clientRepository).findAll();
            verifyNoMoreInteractions(clientRepository);
        }
    }

    @Nested
    @DisplayName("Ao buscar uma entidade de cliente por ID")
    class FindEntityByIdTests {

        @Test
        @DisplayName("Deve retornar Cliente quando ID existir.")
        void shouldReturnClientEntityWhenIdExists() {
            Client client = createClient();
            ReflectionTestUtils.setField(client, "id", 1L);

            when(clientRepository.findById(1L))
                    .thenReturn(Optional.of(client));

            Client result = clientService.findEntityById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(client.getName(), result.getName());
            assertEquals(client.getCpf(), result.getCpf());

            verify(clientRepository).findById(1L);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Deve lançar exceção quando Cliente não existir")
        void shouldThrowExceptionWhenClientDoesNotExist() {
            Long nonExistentId = 1L;

            when(clientRepository.findById(nonExistentId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ClientNotFoundException.class,
                    () -> clientService.findEntityById(nonExistentId)
            );

            verify(clientRepository).findById(nonExistentId);
            verifyNoMoreInteractions(clientRepository);
        }
    }

    @Nested
    @DisplayName("Ao buscar um cliente por ID")
    class FindByIdTests {

        @Test
        @DisplayName("Deve retornar ClientResponseDTO quando ID existir")
        void shouldReturnClientResponseDTOWhenIdExists() {
            Client client = createClient();
            ReflectionTestUtils.setField(client, "id", 1L);

            when(clientRepository.findById(1L))
                    .thenReturn(Optional.of(client));

            ClientResponseDTO result = clientService.findById(1L);

            assertNotNull(result);
            assertEquals(client.getId(), result.id());
            assertEquals(client.getName(), result.name());
            assertEquals(client.getCpf(), result.cpf());

            verify(clientRepository).findById(1L);
            verifyNoMoreInteractions(clientRepository);
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID não existir")
        void shouldThrowExceptionWhenClientDoesNotExist() {
            Long nonExistentId = 1L;

            when(clientRepository.findById(nonExistentId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ClientNotFoundException.class,
                    () -> clientService.findById(nonExistentId)
            );

            verify(clientRepository).findById(nonExistentId);
            verifyNoMoreInteractions(clientRepository);
        }
    }

    @Nested
    @DisplayName("Ao atualizar um cliente")
    class UpdateTests {

        private ClientUpdateRequestDTO createClientUpdateRequestDTO() {
            AddressUpdateRequestDTO address = new AddressUpdateRequestDTO(
                    "456",
                    "Apto 22",
                    "87654321"
            );

            return new ClientUpdateRequestDTO(
                    "Cliente Atualizado",
                    "atualizado@email.com",
                    "11888888888",
                    address
            );
        }

        @Test
        @DisplayName("Deve atualizar cliente com sucesso quando cliente existir")
        void shouldUpdateClientSuccessfullyWhenClientExists() {
            Long clientId = 1L;
            Client client = createClient();
            ReflectionTestUtils.setField(client, "id", clientId);

            String originalCpf = client.getCpf();

            when(clientRepository.findById(clientId))
                    .thenReturn(Optional.of(client));

            ClientUpdateRequestDTO updatedDto = createClientUpdateRequestDTO();

            when(clientRepository.save(any(Client.class)))
                    .thenReturn(client);

            Client result = clientService.update(clientId, updatedDto);

            assertEquals(clientId, result.getId());
            assertEquals(updatedDto.name(), result.getName());
            assertEquals(originalCpf, result.getCpf());
            assertEquals(updatedDto.email(), result.getEmail());
            assertEquals(updatedDto.phoneNumber(), result.getPhoneNumber());

            verify(clientRepository).findById(clientId);
            verify(clientRepository).save(client);
            verifyNoMoreInteractions(clientRepository);
            verify(addressService).updateFromPostalCode(
                    client.getAddress(),
                    updatedDto.address()
            );
            verifyNoMoreInteractions(addressService);

            verify(eventPublisher).publishEvent(
                    new ClientOperationEvent(clientId, ClientOperationType.UPDATED)
            );
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar atualizar um cliente inexistente")
        void shouldThrowExceptionWhenUpdatingNonExistentClient() {
            Long nonExistentId = 1L;
            ClientUpdateRequestDTO dto = createClientUpdateRequestDTO();

            when(clientRepository.findById(nonExistentId))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ClientNotFoundException.class,
                    () -> clientService.update(nonExistentId, dto)
            );

            verify(clientRepository).findById(nonExistentId);
            verify(clientRepository, never()).save(any(Client.class));
            verifyNoMoreInteractions(clientRepository);
            verifyNoInteractions(addressService);
            verifyNoInteractions(eventPublisher);
        }
    }
}