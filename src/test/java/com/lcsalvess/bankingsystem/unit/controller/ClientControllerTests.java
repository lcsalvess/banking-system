package com.lcsalvess.bankingsystem.unit.controller;

import com.lcsalvess.bankingsystem.controller.ClientController;
import com.lcsalvess.bankingsystem.dto.request.AddressRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.AddressUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.ClientRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.AddressResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.Address;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
        WebMvcTestSecurityConfig.class
)
public class ClientControllerTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.controller.ClientControllerTests#";
    private static final String CLIENTS_URL = "/api/v1/clients";
    private static final String CLIENT_URL = "/api/v1/clients/{id}";

    private static final String INTERNAL_ERROR_MESSAGE = "Ocorreu um erro interno no servidor.";
    private static final String INVALID_BODY_MESSAGE = "Dados da requisição inválidos.";
    private static final String INVALID_PARAM_MESSAGE = "Parâmetro de requisição inválido.";
    private static final String VALIDATION_MESSAGE = "Erro de validação.";
    private static final String CLIENT_NOT_FOUND_MESSAGE = "Cliente não encontrado.";
    private static final String CPF_ALREADY_EXISTS_MESSAGE = "Já existe um cliente cadastrado com este CPF.";
    private static final String POSTAL_CODE_NOT_FOUND_MESSAGE = "CEP 01001000 não encontrado em nenhum provedor.";
    private static final String ADDRESS_UNAVAILABLE_MESSAGE =
            "O serviço de consulta de endereços está temporariamente indisponível.";

    private static final String NAME_BLANK_MESSAGE = "O nome não pode ser vazio.";
    private static final String NAME_TOO_LONG_MESSAGE = "O nome deve ter no máximo 125 caracteres.";
    private static final String EMAIL_BLANK_MESSAGE = "O e-mail não pode ser vazio.";
    private static final String EMAIL_INVALID_MESSAGE = "O formato do e-mail é inválido.";
    private static final String EMAIL_TOO_LONG_MESSAGE = "O e-mail deve ter no máximo 150 caracteres.";
    private static final String PHONE_INVALID_MESSAGE = "O telefone deve conter de 10 a 11 números, incluindo o DDD.";
    private static final String STREET_NUMBER_BLANK_MESSAGE = "O número é obrigatório.";
    private static final String STREET_NUMBER_TOO_LONG_MESSAGE = "O número deve ter no máximo 10 caracteres.";
    private static final String COMPLEMENT_TOO_LONG_MESSAGE = "O complemento deve ter no máximo 100 caracteres.";
    private static final String POSTAL_CODE_REQUIRED_MESSAGE = "O CEP é obrigatório.";
    private static final String POSTAL_CODE_INVALID_MESSAGE =
            "O CEP deve conter exatamente 8 números, sem traços ou espaços.";

    private static final String VALID_NAME = "Cliente Teste";
    private static final String VALID_CPF = "52998224725";
    private static final String VALID_EMAIL = "teste@email.com";
    private static final String VALID_PHONE = "11999999999";
    private static final String UPDATED_NAME = "Cliente Atualizado";
    private static final String UPDATED_EMAIL = "atualizado@email.com";
    private static final String VALID_STREET_NUMBER = "123";
    private static final String VALID_CREATION_POSTAL_CODE = "01001000";
    private static final String VALID_UPDATE_POSTAL_CODE = "08710000";

    // Formato válido (parte local curta, rótulos de domínio <= 63), 188 caracteres:
    // viola somente o @Size(max = 150).
    private static final String EMAIL_ABOVE_LIMIT =
            "a@" + "b".repeat(60) + "." + "b".repeat(60) + "." + "b".repeat(60) + ".com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @Nested
    @DisplayName("GET /api/v1/clients")
    class FindAll {

        @Test
        @DisplayName("Should return all clients successfully")
        void shouldReturnAllClientsSuccessfully() throws Exception {
            ClientSummaryResponseDTO client1 = new ClientSummaryResponseDTO(
                    1L, "Lucas Alves", "62934118037", "lucas@email.com", "11999999999"
            );
            ClientSummaryResponseDTO client2 = new ClientSummaryResponseDTO(
                    2L, "Maria Silva", "91741354064", "maria@email.com", "11988888888"
            );

            when(clientService.findAll()).thenReturn(List.of(client1, client2));

            MvcResult result = mockMvc.perform(get(CLIENTS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andReturn();

            assertEquals(List.of(client1, client2), List.of(readClients(result)));

            verify(clientService).findAll();
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return empty list when there are no clients")
        void shouldReturnEmptyListWhenThereAreNoClients() throws Exception {
            when(clientService.findAll()).thenReturn(List.of());

            mockMvc.perform(get(CLIENTS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(clientService).findAll();
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findAll()).thenThrow(new RuntimeException("Unexpected exception"));

            performErrorResponse(mockMvc.perform(get(CLIENTS_URL)), 500, INTERNAL_ERROR_MESSAGE);

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
            ClientResponseDTO expected = clientResponse(VALID_NAME, VALID_EMAIL);

            when(clientService.findById(1L)).thenReturn(expected);

            MvcResult result = mockMvc.perform(get(CLIENT_URL, 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(expected, readClient(result));

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            when(clientService.findById(1L))
                    .thenThrow(new ClientNotFoundException(CLIENT_NOT_FOUND_MESSAGE));

            performErrorResponse(mockMvc.perform(get(CLIENT_URL, 1L)), 404, CLIENT_NOT_FOUND_MESSAGE);

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            performErrorResponse(mockMvc.perform(get(CLIENT_URL, "abc")), 400, INVALID_PARAM_MESSAGE);

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findById(1L)).thenThrow(new RuntimeException("Unexpected exception"));

            performErrorResponse(mockMvc.perform(get(CLIENT_URL, 1L)), 500, INTERNAL_ERROR_MESSAGE);

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
            ClientResponseDTO expected = clientResponse(VALID_NAME, VALID_EMAIL);

            when(clientService.create(request)).thenReturn(expected);

            MvcResult result = postClient(request)
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(expected, readClient(result));

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidClientRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                ClientRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(postClient(request), field, message);

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(post(CLIENTS_URL));

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when postal code is not found")
        void shouldReturnNotFoundWhenPostalCodeIsNotFound() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new PostalCodeNotFoundException(POSTAL_CODE_NOT_FOUND_MESSAGE));

            performErrorResponse(postClient(request), 404, POSTAL_CODE_NOT_FOUND_MESSAGE);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 409 when CPF already exists")
        void shouldReturnConflictWhenCpfAlreadyExists() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new ClientCpfAlreadyExistsException(CPF_ALREADY_EXISTS_MESSAGE));

            performErrorResponse(postClient(request), 409, CPF_ALREADY_EXISTS_MESSAGE);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 503 when address provider is unavailable")
        void shouldReturnServiceUnavailableWhenAddressProviderIsUnavailable() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new AddressProviderUnavailableException("Serviços de CEP indisponíveis no momento."));

            performErrorResponse(postClient(request), 503, ADDRESS_UNAVAILABLE_MESSAGE);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientRequestDTO request = validClientRequest();

            when(clientService.create(request)).thenThrow(new RuntimeException("Unexpected exception"));

            performErrorResponse(postClient(request), 500, INTERNAL_ERROR_MESSAGE);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/clients/{id}")
    class Update {

        @Test
        @DisplayName("Should update client successfully")
        void shouldUpdateClientSuccessfully() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();
            ClientResponseDTO expected = clientResponse(UPDATED_NAME, UPDATED_EMAIL);

            when(clientService.update(1L, request)).thenReturn(expected);

            MvcResult result = patchClient(1L, request)
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(expected, readClient(result));

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should accept an empty JSON object since every field is optional")
        void shouldAcceptEmptyJsonObject() throws Exception {
            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(null, null, null, null);
            ClientResponseDTO expected = clientResponse(VALID_NAME, VALID_EMAIL);

            when(clientService.update(1L, request)).thenReturn(expected);

            mockMvc.perform(patch(CLIENT_URL, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should accept an empty complement to clear it")
        void shouldAcceptEmptyComplement() throws Exception {
            ClientUpdateRequestDTO request = new ClientUpdateRequestDTO(
                    null, null, null, new AddressUpdateRequestDTO(VALID_STREET_NUMBER, "", VALID_UPDATE_POSTAL_CODE)
            );
            ClientResponseDTO expected = clientResponse(VALID_NAME, VALID_EMAIL);

            when(clientService.update(1L, request)).thenReturn(expected);

            patchClient(1L, request)
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidClientUpdateRequests")
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturnBadRequestWhenRequestBodyIsInvalid(
                String scenario,
                ClientUpdateRequestDTO request,
                String field,
                String message
        ) throws Exception {
            performValidationError(patchClient(1L, request), field, message);

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when request body is missing")
        void shouldReturnBadRequestWhenRequestBodyIsMissing() throws Exception {
            performMissingBody(patch(CLIENT_URL, 1L));

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            performErrorResponse(
                    patchClient("abc", validClientUpdateRequest()),
                    400,
                    INVALID_PARAM_MESSAGE
            );

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new ClientNotFoundException(CLIENT_NOT_FOUND_MESSAGE));

            performErrorResponse(patchClient(1L, request), 404, CLIENT_NOT_FOUND_MESSAGE);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when postal code is not found")
        void shouldReturnNotFoundWhenPostalCodeIsNotFound() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new PostalCodeNotFoundException(POSTAL_CODE_NOT_FOUND_MESSAGE));

            performErrorResponse(patchClient(1L, request), 404, POSTAL_CODE_NOT_FOUND_MESSAGE);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 503 when address provider is unavailable")
        void shouldReturnServiceUnavailableWhenAddressProviderIsUnavailable() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new AddressProviderUnavailableException("Serviços de CEP indisponíveis no momento."));

            performErrorResponse(patchClient(1L, request), 503, ADDRESS_UNAVAILABLE_MESSAGE);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientUpdateRequestDTO request = validClientUpdateRequest();

            when(clientService.update(1L, request)).thenThrow(new RuntimeException("Unexpected exception"));

            performErrorResponse(patchClient(1L, request), 500, INTERNAL_ERROR_MESSAGE);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }
    }

    // Cada cenário tem UMA violação: o handler (HashMap) guarda uma única mensagem por campo.
    // Por isso não se usa "" em CPF, telefone, CEP e número (@NotBlank + @Pattern falhariam juntos).
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidClientRequests() {
        AddressRequestDTO address = validAddressRequest();

        return Stream.of(
                Arguments.of("name is null",
                        new ClientRequestDTO(null, VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", "O nome é obrigatório."),
                Arguments.of("name is blank",
                        new ClientRequestDTO("   ", VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", "O nome é obrigatório."),
                Arguments.of("name exceeds 125 characters",
                        new ClientRequestDTO("a".repeat(126), VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", NAME_TOO_LONG_MESSAGE),
                Arguments.of("cpf is null",
                        new ClientRequestDTO(VALID_NAME, null, VALID_EMAIL, VALID_PHONE, address),
                        "cpf", "O CPF é obrigatório."),
                Arguments.of("cpf does not have 11 digits",
                        new ClientRequestDTO(VALID_NAME, "1234", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", "O CPF deve conter exatamente 11 números."),
                Arguments.of("cpf contains non-numeric characters",
                        new ClientRequestDTO(VALID_NAME, "5299822472a", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", "O CPF deve conter exatamente 11 números."),
                Arguments.of("cpf has invalid check digits",
                        new ClientRequestDTO(VALID_NAME, "52998224724", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", "CPF inválido."),
                Arguments.of("email is null",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, null, VALID_PHONE, address),
                        "email", "O e-mail é obrigatório."),
                Arguments.of("email is empty",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, "", VALID_PHONE, address),
                        "email", "O e-mail é obrigatório."),
                Arguments.of("email format is invalid",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, "email-invalido", VALID_PHONE, address),
                        "email", EMAIL_INVALID_MESSAGE),
                Arguments.of("email exceeds 150 characters",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, EMAIL_ABOVE_LIMIT, VALID_PHONE, address),
                        "email", EMAIL_TOO_LONG_MESSAGE),
                Arguments.of("phone number is null",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, VALID_EMAIL, null, address),
                        "phoneNumber", "O telefone é obrigatório."),
                Arguments.of("phone number has fewer than 10 digits",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, VALID_EMAIL, "123", address),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("phone number has more than 11 digits",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, VALID_EMAIL, "119999999999", address),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("phone number contains non-numeric characters",
                        new ClientRequestDTO(VALID_NAME, VALID_CPF, VALID_EMAIL, "1199999999a", address),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("address is null",
                        clientRequest(null),
                        "address", "Os dados de endereço são obrigatórios."),
                Arguments.of("address street number is null",
                        clientRequest(new AddressRequestDTO(null, null, "01001000")),
                        "address.streetNumber", STREET_NUMBER_BLANK_MESSAGE),
                Arguments.of("address street number is empty",
                        clientRequest(new AddressRequestDTO("", null, "01001000")),
                        "address.streetNumber", STREET_NUMBER_BLANK_MESSAGE),
                Arguments.of("address street number exceeds 10 characters",
                        clientRequest(new AddressRequestDTO("12345678901", null, "01001000")),
                        "address.streetNumber", STREET_NUMBER_TOO_LONG_MESSAGE),
                Arguments.of("address complement exceeds 100 characters",
                        clientRequest(new AddressRequestDTO("123", "a".repeat(101), "01001000")),
                        "address.complement", COMPLEMENT_TOO_LONG_MESSAGE),
                Arguments.of("address postal code is null",
                        clientRequest(new AddressRequestDTO("123", null, null)),
                        "address.postalCode", "O CEP é obrigatório."),
                Arguments.of("address postal code has fewer than 8 digits",
                        clientRequest(new AddressRequestDTO("123", null, "1234567")),
                        "address.postalCode", POSTAL_CODE_INVALID_MESSAGE),
                Arguments.of("address postal code contains a dash",
                        clientRequest(new AddressRequestDTO("123", null, "01001-000")),
                        "address.postalCode", POSTAL_CODE_INVALID_MESSAGE)
        );
    }

    // Os campos do cliente são opcionais no PATCH.
// Quando o objeto address é informado, streetNumber e postalCode são obrigatórios.
// Cada cenário deve conter apenas uma violação de validação.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidClientUpdateRequests() {
        return Stream.of(
                Arguments.of("name is empty",
                        new ClientUpdateRequestDTO("", null, null, null),
                        "name", NAME_BLANK_MESSAGE),
                Arguments.of("name exceeds 125 characters",
                        new ClientUpdateRequestDTO("a".repeat(126), null, null, null),
                        "name", NAME_TOO_LONG_MESSAGE),
                Arguments.of("email is empty",
                        new ClientUpdateRequestDTO(null, "", null, null),
                        "email", EMAIL_BLANK_MESSAGE),
                Arguments.of("email format is invalid",
                        new ClientUpdateRequestDTO(null, "email-invalido", null, null),
                        "email", EMAIL_INVALID_MESSAGE),
                Arguments.of("email exceeds 150 characters",
                        new ClientUpdateRequestDTO(null, EMAIL_ABOVE_LIMIT, null, null),
                        "email", EMAIL_TOO_LONG_MESSAGE),
                Arguments.of("phone number is empty",
                        new ClientUpdateRequestDTO(null, null, "", null),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("phone number has fewer than 10 digits",
                        new ClientUpdateRequestDTO(null, null, "123", null),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("phone number has more than 11 digits",
                        new ClientUpdateRequestDTO(null, null, "119999999999", null),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("phone number contains non-numeric characters",
                        new ClientUpdateRequestDTO(null, null, "1199999999a", null),
                        "phoneNumber", PHONE_INVALID_MESSAGE),
                Arguments.of("address street number is empty",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                "", null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", STREET_NUMBER_BLANK_MESSAGE),
                Arguments.of("address street number exceeds 10 characters",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                "12345678901", null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", STREET_NUMBER_TOO_LONG_MESSAGE),
                Arguments.of("address complement exceeds 100 characters",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                VALID_STREET_NUMBER, "a".repeat(101), VALID_UPDATE_POSTAL_CODE)),
                        "address.complement", COMPLEMENT_TOO_LONG_MESSAGE),
                Arguments.of("address postal code has fewer than 8 digits",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                VALID_STREET_NUMBER, null, "1234567")),
                        "address.postalCode", POSTAL_CODE_INVALID_MESSAGE),
                Arguments.of("address postal code contains a dash",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                VALID_STREET_NUMBER, null, "01001-000")),
                        "address.postalCode", POSTAL_CODE_INVALID_MESSAGE),
                Arguments.of("address street number is null",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                null, null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", STREET_NUMBER_BLANK_MESSAGE),
                Arguments.of("address postal code is null",
                        clientUpdateRequest(new AddressUpdateRequestDTO(
                                VALID_STREET_NUMBER, null, null)),
                        "address.postalCode", POSTAL_CODE_REQUIRED_MESSAGE)
        );
    }

    private ResultActions postClient(Object request) throws Exception {
        return mockMvc.perform(post(CLIENTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions patchClient(Object id, Object request) throws Exception {
        return mockMvc.perform(patch(CLIENT_URL, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void performErrorResponse(
            ResultActions result,
            int status,
            String message
    ) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    // Colchetes: campos aninhados chegam como chave plana ("address.streetNumber"),
    // e "$.errors.address.streetNumber" seria lido como caminho aninhado.
    private void performValidationError(
            ResultActions result,
            String field,
            String message
    ) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                .andExpect(jsonPath("$.errors['" + field + "']").value(message));
    }

    private void performMissingBody(MockHttpServletRequestBuilder request) throws Exception {
        performErrorResponse(
                mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)),
                400,
                INVALID_BODY_MESSAGE
        );
    }

    private ClientResponseDTO readClient(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClientResponseDTO.class
        );
    }

    private ClientSummaryResponseDTO[] readClients(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClientSummaryResponseDTO[].class
        );
    }

    private static AddressRequestDTO validAddressRequest() {
        return new AddressRequestDTO(VALID_STREET_NUMBER, null, VALID_CREATION_POSTAL_CODE);
    }

    private static ClientRequestDTO validClientRequest() {
        return clientRequest(validAddressRequest());
    }

    private static ClientRequestDTO clientRequest(AddressRequestDTO address) {
        return new ClientRequestDTO(VALID_NAME, VALID_CPF, VALID_EMAIL, VALID_PHONE, address);
    }

    private static AddressUpdateRequestDTO validAddressUpdateRequest() {
        return new AddressUpdateRequestDTO(VALID_STREET_NUMBER, null, VALID_UPDATE_POSTAL_CODE);
    }

    private static ClientUpdateRequestDTO validClientUpdateRequest() {
        return new ClientUpdateRequestDTO(UPDATED_NAME, UPDATED_EMAIL, VALID_PHONE, validAddressUpdateRequest());
    }

    private static ClientUpdateRequestDTO clientUpdateRequest(AddressUpdateRequestDTO address) {
        return new ClientUpdateRequestDTO(null, null, null, address);
    }

    private static Address address() {
        return new Address(
                "Praça da Sé",
                VALID_STREET_NUMBER,
                null,
                "Sé",
                "São Paulo",
                State.SP,
                VALID_CREATION_POSTAL_CODE
        );
    }

    private static ClientResponseDTO clientResponse(String name, String email) {
        return new ClientResponseDTO(
                1L,
                name,
                VALID_CPF,
                email,
                VALID_PHONE,
                AddressResponseDTO.fromEntity(address())
        );
    }
}