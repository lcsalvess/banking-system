package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.AddressRequestDTO;
import com.lucas.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.entity.enums.StreetType;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.service.ClientService;
import com.lucas.bankingsystem.service.security.CustomUserDetailsService;
import com.lucas.bankingsystem.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ClientControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("GET /api/v1/clients")
    class FindAll {

        @Test
        @DisplayName("Should return all clients successfully")
        void shouldReturnAllClientsSuccessfully() throws Exception {
            ClientResponseDTO client1 = new ClientResponseDTO(
                    1L,
                    "Lucas Alves",
                    "62934118037",
                    "lucas@email.com",
                    "11999999999"
            );
            ClientResponseDTO client2 = new ClientResponseDTO(
                    2L,
                    "Maria Silva",
                    "91741354064",
                    "maria@email.com",
                    "11988888888"
            );

            when(clientService.findAll()).thenReturn(List.of(client1, client2));

            mockMvc.perform(get("/api/v1/clients"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(client1.id()))
                    .andExpect(jsonPath("$[0].name").value(client1.name()))
                    .andExpect(jsonPath("$[0].cpf").value(client1.cpf()))
                    .andExpect(jsonPath("$[0].email").value(client1.email()))
                    .andExpect(jsonPath("$[0].phoneNumber").value(client1.phoneNumber()))
                    .andExpect(jsonPath("$[1].id").value(client2.id()))
                    .andExpect(jsonPath("$[1].name").value(client2.name()))
                    .andExpect(jsonPath("$[1].cpf").value(client2.cpf()))
                    .andExpect(jsonPath("$[1].email").value(client2.email()))
                    .andExpect(jsonPath("$[1].phoneNumber").value(client2.phoneNumber()));

            verify(clientService).findAll();
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return empty list when there are no clients")
        void shouldReturnEmptyListWhenThereAreNoClients() throws Exception {
            when(clientService.findAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/clients"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(clientService).findAll();
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findAll()).thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(get("/api/v1/clients"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(clientService).findAll();
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clients/{id}")
    class FindById {

        @Test
        @DisplayName("Should return client successfully when client exists")
        void shouldReturnClientWhenClientExists() throws Exception {
            ClientResponseDTO client = new ClientResponseDTO(
                    1L,
                    "Lucas Alves",
                    "52998224725",
                    "lucas@email.com",
                    "11999999999"
            );

            when(clientService.findById(1L)).thenReturn(client);

            mockMvc.perform(get("/api/v1/clients/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(client.id()))
                    .andExpect(jsonPath("$.name").value(client.name()))
                    .andExpect(jsonPath("$.cpf").value(client.cpf()))
                    .andExpect(jsonPath("$.email").value(client.email()))
                    .andExpect(jsonPath("$.phoneNumber").value(client.phoneNumber()));

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            when(clientService.findById(1L))
                    .thenThrow(new ClientNotFoundException("Cliente não encontrado."));

            mockMvc.perform(get("/api/v1/clients/{id}", 1L))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Cliente não encontrado."));

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/clients/{id}", "abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findById(1L)).thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(get("/api/v1/clients/{id}", 1L))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clients")
    class Save {

        @Test
        @DisplayName("Should create client successfully")
        void shouldCreateClientSuccessfully() throws Exception {
            ClientRequestDTO request = validClientRequest();
            Client savedClient = mockClient(
                    "Cliente Teste", "teste@email.com"
            );

            when(clientService.save(any(ClientRequestDTO.class))).thenReturn(savedClient);

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedClient.getId()))
                    .andExpect(jsonPath("$.name").value(savedClient.getName()))
                    .andExpect(jsonPath("$.cpf").value(savedClient.getCpf()))
                    .andExpect(jsonPath("$.email").value(savedClient.getEmail()))
                    .andExpect(jsonPath("$.phoneNumber").value(savedClient.getPhoneNumber()));

            verify(clientService).save(eq(request));
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when CPF is invalid")
        void shouldReturnBadRequestWhenCpfIsInvalid() throws Exception {
            ClientRequestDTO request = validClientRequest("52998224724");

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
            ClientRequestDTO request = new ClientRequestDTO(
                    "",
                    "52998224725",
                    "email-invalido",
                    "123",
                    validAddressRequest()
            );

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 409 when CPF already exists")
        void shouldReturnConflictWhenCpfAlreadyExists() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.save(any(ClientRequestDTO.class)))
                    .thenThrow(new ClientCpfAlreadyExistsException("CPF já cadastrado: 52998224725"));

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("CPF já cadastrado: 52998224725"));

            verify(clientService).save(eq(request));
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.save(any(ClientRequestDTO.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(clientService).save(eq(request));
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/clients/{id}")
    class Update {

        @Test
        @DisplayName("Should update client successfully")
        void shouldUpdateClientSuccessfully() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();
            Client client = mockClient(
                    "Cliente Atualizado", "cliente@email.com"
            );

            when(clientService.update(eq(1L), any(ClientUpdateRequestDTO.class))).thenReturn(client);

            mockMvc.perform(
                            put("/api/v1/clients/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(client.getId()))
                    .andExpect(jsonPath("$.name").value(client.getName()))
                    .andExpect(jsonPath("$.cpf").value(client.getCpf()))
                    .andExpect(jsonPath("$.email").value(client.getEmail()))
                    .andExpect(jsonPath("$.phoneNumber").value(client.getPhoneNumber()));

            verify(clientService).update(eq(1L), eq(request));
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(eq(1L), any(ClientUpdateRequestDTO.class)))
                    .thenThrow(new ClientNotFoundException("Cliente não encontrado."));

            mockMvc.perform(
                            put("/api/v1/clients/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Cliente não encontrado."));

            verify(clientService).update(eq(1L), eq(request));
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            mockMvc.perform(
                            put("/api/v1/clients/{id}", "abc")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(
                    "Cliente Atualizado",
                    "email-invalido",
                    "123",
                    validAddressUpdateRequest()
            );

            mockMvc.perform(
                            put("/api/v1/clients/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            mockMvc.perform(
                            put("/api/v1/clients/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(eq(1L), any(ClientUpdateRequestDTO.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(
                            put("/api/v1/clients/{id}", 1L)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Ocorreu um erro interno no servidor."));

            verify(clientService).update(eq(1L), eq(request));
            verifyNoMoreInteractions(clientService);
        }
    }

    private static AddressRequestDTO validAddressRequest() {
        return new AddressRequestDTO(
                StreetType.RUA,
                "Logradouro Teste",
                "123",
                null,
                "Bairro Teste",
                "Cidade Teste",
                State.SP,
                "12345678"
        );
    }

    private static ClientRequestDTO validClientRequest() {
        return validClientRequest("52998224725");
    }

    private static ClientRequestDTO validClientRequest(String cpf) {
        return new ClientRequestDTO(
                "Cliente Teste",
                cpf,
                "teste@email.com",
                "11999999999",
                validAddressRequest()
        );
    }

    private static AddressUpdateRequestDTO validAddressUpdateRequest() {
        return new AddressUpdateRequestDTO(
                StreetType.RUA,
                "Rua Teste",
                "123",
                null,
                "Centro",
                "São Paulo",
                State.SP,
                "01001000"
        );
    }

    private static ClientUpdateRequestDTO validClientUpdateRequest() {
        return new ClientUpdateRequestDTO(
                "Cliente Atualizado",
                "cliente@email.com",
                "11999999999",
                validAddressUpdateRequest()
        );
    }

    private static Client mockClient(String name, String email) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(1L);
        when(client.getName()).thenReturn(name);
        when(client.getCpf()).thenReturn("52998224725");
        when(client.getEmail()).thenReturn(email);
        when(client.getPhoneNumber()).thenReturn("11999999999");
        return client;
    }
}