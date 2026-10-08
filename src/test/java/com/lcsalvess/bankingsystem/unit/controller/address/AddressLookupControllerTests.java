package com.lcsalvess.bankingsystem.unit.controller.address;

import com.lcsalvess.bankingsystem.controller.address.AddressLookupController;
import com.lcsalvess.bankingsystem.dto.response.AddressLookupResponseDTO;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
        WebMvcTestSecurityConfig.class
)
class AddressLookupControllerTests {

    private static final String PROVIDER = "com.lcsalvess.bankingsystem.unit.controller.address.AddressLookupControllerTests#";
    private static final String POSTAL_CODE = "01001000";
    private static final String VALIDATION_MESSAGE = "Erro de validação.";
    private static final String INVALID_POSTAL_CODE_MESSAGE = "O CEP deve conter exatamente 8 dígitos.";
    private static final String POSTAL_CODE_NOT_FOUND_MESSAGE = "CEP " + POSTAL_CODE + " não encontrado em nenhum provedor.";
    private static final String PROVIDER_UNAVAILABLE_MESSAGE = "O serviço de consulta de endereços está temporariamente indisponível.";
    private static final String INTERNAL_ERROR_MESSAGE = "Ocorreu um erro interno no servidor.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Nested
    @DisplayName("GET /api/v1/addresses/lookup/{postalCode}")
    class FindByPostalCode {

        private static final String URL = "/api/v1/addresses/lookup/{postalCode}";

        @Test
        @DisplayName("Should return the address successfully")
        void shouldReturnTheAddressSuccessfully() throws Exception {
            when(addressService.lookupByPostalCode(POSTAL_CODE)).thenReturn(lookupResponse());

            mockMvc.perform(get(URL, POSTAL_CODE))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.streetName").value("Praça da Sé"))
                    .andExpect(jsonPath("$.neighborhood").value("Sé"))
                    .andExpect(jsonPath("$.city").value("São Paulo"))
                    .andExpect(jsonPath("$.state").value("SP"))
                    .andExpect(jsonPath("$.postalCode").value(POSTAL_CODE));

            verify(addressService).lookupByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressService);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "invalidPostalCodes")
        @DisplayName("Should return 400 when postal code is invalid")
        void shouldReturnBadRequestWhenPostalCodeIsInvalid(String scenario, String postalCode) throws Exception {
            mockMvc.perform(get(URL, postalCode))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(VALIDATION_MESSAGE))
                    .andExpect(jsonPath("$.errors.postalCode").value(INVALID_POSTAL_CODE_MESSAGE));

            verifyNoInteractions(addressService);
        }

        @Test
        @DisplayName("Should return 404 when postal code is not found")
        void shouldReturnNotFoundWhenPostalCodeIsNotFound() throws Exception {
            when(addressService.lookupByPostalCode(POSTAL_CODE))
                    .thenThrow(new PostalCodeNotFoundException(POSTAL_CODE_NOT_FOUND_MESSAGE));

            performErrorResponse(performGet(), 404, POSTAL_CODE_NOT_FOUND_MESSAGE);

            verify(addressService).lookupByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressService);
        }

        @Test
        @DisplayName("Should return 503 when address providers are unavailable")
        void shouldReturnServiceUnavailableWhenAddressProvidersAreUnavailable() throws Exception {
            when(addressService.lookupByPostalCode(POSTAL_CODE))
                    .thenThrow(new AddressProviderUnavailableException(
                            "Serviços de CEP indisponíveis no momento. Não foi possível validar o CEP: " + POSTAL_CODE));

            performErrorResponse(performGet(), 503, PROVIDER_UNAVAILABLE_MESSAGE);

            verify(addressService).lookupByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressService);
        }

        @Test
        @DisplayName("Should return 500 when an unexpected error occurs")
        void shouldReturnInternalServerErrorWhenAnUnexpectedErrorOccurs() throws Exception {
            when(addressService.lookupByPostalCode(POSTAL_CODE))
                    .thenThrow(new RuntimeException("Unexpected failure"));

            performErrorResponse(performGet(), 500, INTERNAL_ERROR_MESSAGE);

            verify(addressService).lookupByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressService);
        }

        private ResultActions performGet() throws Exception {
            return mockMvc.perform(get(URL, POSTAL_CODE));
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> invalidPostalCodes() {
        return Stream.of(
                Arguments.of("postal code has less than 8 digits", "0100100"),
                Arguments.of("postal code has more than 8 digits", "010010000"),
                Arguments.of("postal code contains letters", "0100100A"),
                Arguments.of("postal code contains a hyphen", "01001-00")
        );
    }

    private void performErrorResponse(ResultActions result, int status, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    private static AddressLookupResponseDTO lookupResponse() {
        return new AddressLookupResponseDTO("Praça da Sé", "Sé", "São Paulo", "SP", POSTAL_CODE);
    }
}