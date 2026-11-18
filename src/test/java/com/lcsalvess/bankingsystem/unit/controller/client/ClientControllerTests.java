package com.lcsalvess.bankingsystem.unit.controller.client;

import com.lcsalvess.bankingsystem.config.pagination.PaginationConfig;
import com.lcsalvess.bankingsystem.controller.client.ClientController;
import com.lcsalvess.bankingsystem.dto.request.address.AddressRequestDto;
import com.lcsalvess.bankingsystem.dto.request.address.AddressUpdateRequestDto;
import com.lcsalvess.bankingsystem.dto.request.client.ClientRequestDto;
import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDto;
import com.lcsalvess.bankingsystem.dto.response.address.AddressResponseDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientResponseDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientSummaryResponseDto;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.address.State;
import com.lcsalvess.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.unit.config.WebMvcTestSecurityConfig;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcTestSecurityConfig.class,
        PaginationConfig.class
})
public class ClientControllerTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.controller.client.ClientControllerTests#";
    private static final String CLIENTS_URL = "/api/v1/clients";
    private static final String CLIENT_URL = "/api/v1/clients/{clientId}";

    private static final String VALID_NAME = "Test Client";
    private static final String VALID_CPF = "52998224725";
    private static final String VALID_EMAIL = "test.client@email.com";
    private static final String VALID_PHONE = "11999999999";
    private static final String UPDATED_NAME = "Updated Client";
    private static final String UPDATED_EMAIL = "updated.client@email.com";
    private static final String VALID_STREET_NUMBER = "123";
    private static final String VALID_CREATION_POSTAL_CODE = "01001000";
    private static final String VALID_UPDATE_POSTAL_CODE = "08710000";

    // Viola somente o @Size(max = 254).
    private static final String EMAIL_ABOVE_LIMIT =
            "a".repeat(64) + "@"
                    + "b".repeat(63) + "."
                    + "c".repeat(63) + "."
                    + "d".repeat(60) + ".com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @Nested
    @DisplayName("GET /api/v1/clients")
    class FindAll {

        private final Pageable defaultPageable = PageRequest.of(
                0,
                10,
                Sort.by("id").ascending()
        );

        @Test
        @DisplayName("Should return a page of clients successfully")
        void shouldReturnPageOfClientsSuccessfully() throws Exception {
            List<ClientSummaryResponseDto> clientSummaries = clientSummaries();

            Page<ClientSummaryResponseDto> clients = new PageImpl<>(
                    clientSummaries,
                    defaultPageable,
                    clientSummaries.size()
            );

            when(clientService.findAll(null, defaultPageable)).thenReturn(clients);

            mockMvc.perform(get(CLIENTS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(10))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1));

            verify(clientService).findAll(null, defaultPageable);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return an empty page when there are no clients")
        void shouldReturnEmptyPageWhenThereAreNoClients() throws Exception {
            Page<ClientSummaryResponseDto> emptyPage = new PageImpl<>(
                    List.of(),
                    defaultPageable,
                    0
            );

            when(clientService.findAll(null, defaultPageable)).thenReturn(emptyPage);

            mockMvc.perform(get(CLIENTS_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));

            verify(clientService).findAll(null, defaultPageable);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should apply pagination and name filter parameters")
        void shouldApplyPaginationAndNameFilterParameters() throws Exception {
            Pageable pageable = PageRequest.of(
                    1,
                    5,
                    Sort.by("id").ascending()
            );

            Page<ClientSummaryResponseDto> clients = new PageImpl<>(
                    List.of(),
                    pageable,
                    0
            );

            when(clientService.findAll("Lucas", pageable)).thenReturn(clients);

            mockMvc.perform(get(CLIENTS_URL)
                            .param("name", "Lucas")
                            .param("page", "1")
                            .param("size", "5")
                            .param("sort", "id,asc"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.page.number").value(1))
                    .andExpect(jsonPath("$.page.size").value(5));

            verify(clientService).findAll("Lucas", pageable);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findAll(null, defaultPageable))
                    .thenThrow(new RuntimeException());

            performErrorResponse(
                    mockMvc.perform(get(CLIENTS_URL)),
                    500,
                    ApiErrorMessages.INTERNAL_SERVER_ERROR
            );

            verify(clientService).findAll(null, defaultPageable);
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clients/{clientId}")
    class FindById {

        @Test
        @DisplayName("Should return client successfully when client exists")
        void shouldReturnClientWhenClientExists() throws Exception {
            ClientResponseDto expected = clientResponse(VALID_NAME, VALID_EMAIL);

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
                    .thenThrow(new ClientNotFoundException(ApiErrorMessages.CLIENT_NOT_FOUND));

            performErrorResponse(mockMvc.perform(get(CLIENT_URL, 1L)), 404, ApiErrorMessages.CLIENT_NOT_FOUND);

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
            performErrorResponse(mockMvc.perform(get(CLIENT_URL, "abc")), 400, ApiErrorMessages.INVALID_REQUEST_PARAMETER);

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            when(clientService.findById(1L)).thenThrow(new RuntimeException());

            performErrorResponse(mockMvc.perform(get(CLIENT_URL, 1L)), 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(clientService).findById(1L);
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/clients")
    class Create {

        @Test
        @DisplayName("Should create client successfully")
        void shouldCreateClientSuccessfully() throws Exception {
            ClientRequestDto request = validClientRequest();
            ClientResponseDto expected = clientResponse(VALID_NAME, VALID_EMAIL);

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
                ClientRequestDto request,
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
            ClientRequestDto request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new PostalCodeNotFoundException(ApiErrorMessages.POSTAL_CODE_NOT_FOUND));

            performErrorResponse(postClient(request), 404, ApiErrorMessages.POSTAL_CODE_NOT_FOUND);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 409 when CPF already exists")
        void shouldReturnConflictWhenCpfAlreadyExists() throws Exception {
            ClientRequestDto request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new ClientCpfAlreadyExistsException(ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS));

            performErrorResponse(postClient(request), 409, ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 409 when email already exists")
        void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
            ClientRequestDto request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new ClientEmailAlreadyExistsException(ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS));

            performErrorResponse(postClient(request), 409, ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 503 when address provider is unavailable")
        void shouldReturnServiceUnavailableWhenAddressProviderIsUnavailable() throws Exception {
            ClientRequestDto request = validClientRequest();

            when(clientService.create(request))
                    .thenThrow(new AddressProviderUnavailableException(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE));

            performErrorResponse(postClient(request), 503, ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientRequestDto request = validClientRequest();

            when(clientService.create(request)).thenThrow(new RuntimeException());

            performErrorResponse(postClient(request), 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(clientService).create(request);
            verifyNoMoreInteractions(clientService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/clients/{clientId}")
    class Update {

        @Test
        @DisplayName("Should update client successfully")
        void shouldUpdateClientSuccessfully() throws Exception {
            ClientUpdateRequestDto request = validClientUpdateRequest();
            ClientResponseDto expected = clientResponse(UPDATED_NAME, UPDATED_EMAIL);

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
            ClientUpdateRequestDto request = new ClientUpdateRequestDto(null, null, null, null);
            ClientResponseDto expected = clientResponse(VALID_NAME, VALID_EMAIL);

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
            ClientUpdateRequestDto request = new ClientUpdateRequestDto(
                    null, null, null, new AddressUpdateRequestDto(VALID_STREET_NUMBER, "", VALID_UPDATE_POSTAL_CODE)
            );
            ClientResponseDto expected = clientResponse(VALID_NAME, VALID_EMAIL);

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
                ClientUpdateRequestDto request,
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
                    ApiErrorMessages.INVALID_REQUEST_PARAMETER
            );

            verifyNoInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when client does not exist")
        void shouldReturnNotFoundWhenClientDoesNotExist() throws Exception {
            ClientUpdateRequestDto request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new ClientNotFoundException(ApiErrorMessages.CLIENT_NOT_FOUND));

            performErrorResponse(patchClient(1L, request), 404, ApiErrorMessages.CLIENT_NOT_FOUND);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 404 when postal code is not found")
        void shouldReturnNotFoundWhenPostalCodeIsNotFound() throws Exception {
            ClientUpdateRequestDto request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new PostalCodeNotFoundException(ApiErrorMessages.POSTAL_CODE_NOT_FOUND));

            performErrorResponse(patchClient(1L, request), 404, ApiErrorMessages.POSTAL_CODE_NOT_FOUND);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 503 when address provider is unavailable")
        void shouldReturnServiceUnavailableWhenAddressProviderIsUnavailable() throws Exception {
            ClientUpdateRequestDto request = validClientUpdateRequest();

            when(clientService.update(1L, request))
                    .thenThrow(new AddressProviderUnavailableException(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE));

            performErrorResponse(patchClient(1L, request), 503, ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }

        @Test
        @DisplayName("Should return 500 when service throws an unexpected exception")
        void shouldReturnInternalServerErrorWhenServiceFails() throws Exception {
            ClientUpdateRequestDto request = validClientUpdateRequest();

            when(clientService.update(1L, request)).thenThrow(new RuntimeException());

            performErrorResponse(patchClient(1L, request), 500, ApiErrorMessages.INTERNAL_SERVER_ERROR);

            verify(clientService).update(1L, request);
            verifyNoMoreInteractions(clientService);
        }
    }

    // Cada cenário tem UMA violação: o handler (HashMap) guarda uma única mensagem por campo.
    // Por isso não se usa "" em CPF, telefone, CEP e número (@NotBlank + @Pattern falhariam juntos).
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidClientRequests() {
        AddressRequestDto address = validAddressRequest();

        return Stream.of(
                Arguments.of("name is null",
                        new ClientRequestDto(null, VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", ValidationMessages.REQUIRED_CLIENT_NAME),
                Arguments.of("name is blank",
                        new ClientRequestDto("   ", VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", ValidationMessages.REQUIRED_CLIENT_NAME),
                Arguments.of("name exceeds 125 characters",
                        new ClientRequestDto("a".repeat(126), VALID_CPF, VALID_EMAIL, VALID_PHONE, address),
                        "name", ValidationMessages.INVALID_CLIENT_NAME_LENGTH),

                Arguments.of("cpf is null",
                        new ClientRequestDto(VALID_NAME, null, VALID_EMAIL, VALID_PHONE, address),
                        "cpf", ValidationMessages.REQUIRED_CPF),
                Arguments.of("cpf does not have 11 digits",
                        new ClientRequestDto(VALID_NAME, "1234", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", ValidationMessages.INVALID_CPF_LENGTH),
                Arguments.of("cpf contains non-numeric characters",
                        new ClientRequestDto(VALID_NAME, "5299822472a", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", ValidationMessages.INVALID_CPF_LENGTH),
                Arguments.of("cpf has invalid check digits",
                        new ClientRequestDto(VALID_NAME, "52998224724", VALID_EMAIL, VALID_PHONE, address),
                        "cpf", ValidationMessages.INVALID_CPF),

                Arguments.of("email is null",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, null, VALID_PHONE, address),
                        "email", ValidationMessages.REQUIRED_EMAIL),
                Arguments.of("email is empty",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, "", VALID_PHONE, address),
                        "email", ValidationMessages.REQUIRED_EMAIL),
                Arguments.of("email format is invalid",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, "email-invalido", VALID_PHONE, address),
                        "email", ValidationMessages.INVALID_EMAIL),
                Arguments.of("email exceeds 254 characters",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, EMAIL_ABOVE_LIMIT, VALID_PHONE, address),
                        "email", ValidationMessages.INVALID_EMAIL_LENGTH),

                Arguments.of("phone number is null",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, VALID_EMAIL, null, address),
                        "phoneNumber", ValidationMessages.REQUIRED_PHONE_NUMBER),
                Arguments.of("phone number has fewer than 10 digits",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, VALID_EMAIL, "123", address),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),
                Arguments.of("phone number has more than 11 digits",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, VALID_EMAIL, "119999999999", address),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),
                Arguments.of("phone number contains non-numeric characters",
                        new ClientRequestDto(VALID_NAME, VALID_CPF, VALID_EMAIL, "1199999999a", address),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),

                Arguments.of("address is null",
                        clientRequest(null),
                        "address", ValidationMessages.REQUIRED_ADDRESS),
                Arguments.of("address street number is null",
                        clientRequest(new AddressRequestDto(null, null, "01001000")),
                        "address.streetNumber", ValidationMessages.REQUIRED_STREET_NUMBER),
                Arguments.of("address street number is empty",
                        clientRequest(new AddressRequestDto("", null, "01001000")),
                        "address.streetNumber", ValidationMessages.REQUIRED_STREET_NUMBER),
                Arguments.of("address street number exceeds 10 characters",
                        clientRequest(new AddressRequestDto("12345678901", null, "01001000")),
                        "address.streetNumber", ValidationMessages.STREET_NUMBER_EXCEEDS_MAX_LENGTH),
                Arguments.of("address complement exceeds 100 characters",
                        clientRequest(new AddressRequestDto("123", "a".repeat(101), "01001000")),
                        "address.complement", ValidationMessages.INVALID_COMPLEMENT_LENGTH),
                Arguments.of("address postal code is null",
                        clientRequest(new AddressRequestDto("123", null, null)),
                        "address.postalCode", ValidationMessages.REQUIRED_POSTAL_CODE),
                Arguments.of("address postal code has fewer than 8 digits",
                        clientRequest(new AddressRequestDto("123", null, "1234567")),
                        "address.postalCode", ValidationMessages.INVALID_POSTAL_CODE_FORMAT),
                Arguments.of("address postal code contains a dash",
                        clientRequest(new AddressRequestDto("123", null, "01001-000")),
                        "address.postalCode", ValidationMessages.INVALID_POSTAL_CODE_FORMAT)
        );
    }

    // Os campos do cliente são opcionais no PATCH.
    // Quando o objeto address é informado, streetNumber e postalCode são obrigatórios.
    // Cada cenário deve conter apenas uma violação de validação.
    @SuppressWarnings("unused") // usado via @MethodSource (referência por String)
    static Stream<Arguments> invalidClientUpdateRequests() {
        return Stream.of(
                Arguments.of("name is empty",
                        new ClientUpdateRequestDto("", null, null, null),
                        "name", ValidationMessages.INVALID_CLIENT_NAME_BLANK),
                Arguments.of("name exceeds 125 characters",
                        new ClientUpdateRequestDto("a".repeat(126), null, null, null),
                        "name", ValidationMessages.INVALID_CLIENT_NAME_LENGTH),

                Arguments.of("email is empty",
                        new ClientUpdateRequestDto(null, "", null, null),
                        "email", ValidationMessages.INVALID_EMAIL_BLANK),
                Arguments.of("email format is invalid",
                        new ClientUpdateRequestDto(null, "email-invalido", null, null),
                        "email", ValidationMessages.INVALID_EMAIL),
                Arguments.of("email exceeds 254 characters",
                        new ClientUpdateRequestDto(null, EMAIL_ABOVE_LIMIT, null, null),
                        "email", ValidationMessages.INVALID_EMAIL_LENGTH),

                Arguments.of("phone number is empty",
                        new ClientUpdateRequestDto(null, null, "", null),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),
                Arguments.of("phone number has fewer than 10 digits",
                        new ClientUpdateRequestDto(null, null, "123", null),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),
                Arguments.of("phone number has more than 11 digits",
                        new ClientUpdateRequestDto(null, null, "119999999999", null),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),
                Arguments.of("phone number contains non-numeric characters",
                        new ClientUpdateRequestDto(null, null, "1199999999a", null),
                        "phoneNumber", ValidationMessages.INVALID_PHONE_NUMBER_FORMAT),

                Arguments.of("address street number is empty",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                "", null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", ValidationMessages.REQUIRED_STREET_NUMBER),
                Arguments.of("address street number exceeds 10 characters",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                "12345678901", null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", ValidationMessages.STREET_NUMBER_EXCEEDS_MAX_LENGTH),
                Arguments.of("address complement exceeds 100 characters",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                VALID_STREET_NUMBER, "a".repeat(101), VALID_UPDATE_POSTAL_CODE)),
                        "address.complement", ValidationMessages.INVALID_COMPLEMENT_LENGTH),

                Arguments.of("address postal code has fewer than 8 digits",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                VALID_STREET_NUMBER, null, "1234567")),
                        "address.postalCode", ValidationMessages.INVALID_POSTAL_CODE_FORMAT),
                Arguments.of("address postal code contains a dash",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                VALID_STREET_NUMBER, null, "01001-000")),
                        "address.postalCode", ValidationMessages.INVALID_POSTAL_CODE_FORMAT),
                Arguments.of("address street number is null",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                null, null, VALID_UPDATE_POSTAL_CODE)),
                        "address.streetNumber", ValidationMessages.REQUIRED_STREET_NUMBER),
                Arguments.of("address postal code is null",
                        clientUpdateRequest(new AddressUpdateRequestDto(
                                VALID_STREET_NUMBER, null, null)),
                        "address.postalCode", ValidationMessages.REQUIRED_POSTAL_CODE)
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
                .andExpect(jsonPath("$.message").value(ApiErrorMessages.VALIDATION_ERROR))
                .andExpect(jsonPath("$.errors['" + field + "']").value(message));
    }

    private void performMissingBody(MockHttpServletRequestBuilder request) throws Exception {
        performErrorResponse(
                mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)),
                400,
                ApiErrorMessages.INVALID_REQUEST_DATA
        );
    }

    private ClientResponseDto readClient(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                ClientResponseDto.class
        );
    }

    private static AddressRequestDto validAddressRequest() {
        return new AddressRequestDto(VALID_STREET_NUMBER, null, VALID_CREATION_POSTAL_CODE);
    }

    private static ClientRequestDto validClientRequest() {
        return clientRequest(validAddressRequest());
    }

    private static ClientRequestDto clientRequest(AddressRequestDto address) {
        return new ClientRequestDto(VALID_NAME, VALID_CPF, VALID_EMAIL, VALID_PHONE, address);
    }

    private static AddressUpdateRequestDto validAddressUpdateRequest() {
        return new AddressUpdateRequestDto(VALID_STREET_NUMBER, null, VALID_UPDATE_POSTAL_CODE);
    }

    private static ClientUpdateRequestDto validClientUpdateRequest() {
        return new ClientUpdateRequestDto(UPDATED_NAME, UPDATED_EMAIL, VALID_PHONE, validAddressUpdateRequest());
    }

    private static ClientUpdateRequestDto clientUpdateRequest(AddressUpdateRequestDto address) {
        return new ClientUpdateRequestDto(null, null, null, address);
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

    private static ClientResponseDto clientResponse(String name, String email) {
        return new ClientResponseDto(
                1L,
                name,
                VALID_CPF,
                email,
                VALID_PHONE,
                AddressResponseDto.fromEntity(address())
        );
    }

    private static List<ClientSummaryResponseDto> clientSummaries() {
        return List.of(
                new ClientSummaryResponseDto(
                        1L, "Joe Michael Doe", "62934118037",
                        "joe.doe@email.com", "11999999999"
                ),
                new ClientSummaryResponseDto(
                        2L, "Jane Michele Doe", "91741354064",
                        "jane.doe@email.com", "11988888888"
                )
        );
    }
}
