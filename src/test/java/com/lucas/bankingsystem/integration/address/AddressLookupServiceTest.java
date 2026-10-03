package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressLookupServiceTest {

    @Mock
    private AddressProvider viaCepClient;

    @Mock
    private AddressProvider brasilApiClient;

    private AddressLookupService addressLookupService;

    private static final String POSTAL_CODE = "01001000";

    private static final String UNAVAILABLE_MESSAGE = "Serviços de CEP indisponíveis no momento. Não foi possível validar o CEP: " + POSTAL_CODE;

    @BeforeEach
    void setUp() {
        addressLookupService = new AddressLookupService(List.of(viaCepClient, brasilApiClient), new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("When ViaCEP finds postal code")
    class WhenViaCepFindsPostalCode {

        @Test
        @DisplayName("Should return address without calling Brasil API")
        void shouldReturnAddressWithoutCallingBrasilApi() {
            AddressLookupResponse expected = expectedAddressResponse();

            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenReturn(expected);

            AddressLookupResponse result = addressLookupService.findByPostalCode(POSTAL_CODE);

            assertEquals(expected, result);
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verifyNoInteractions(brasilApiClient);
            verifyNoMoreInteractions(viaCepClient);
        }
    }

    @Nested
    @DisplayName("When ViaCEP does not find postal code")
    class WhenViaCepDoesNotFindPostalCode {

        @Test
        @DisplayName("Should return address from Brasil API")
        void shouldReturnAddressFromBrasilApi() {
            AddressLookupResponse expected = expectedAddressResponse();

            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new PostalCodeNotFoundException("CEP não encontrado no ViaCEP."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenReturn(expected);

            AddressLookupResponse result = addressLookupService.findByPostalCode(POSTAL_CODE);

            assertEquals(expected, result);
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    @Nested
    @DisplayName("When ViaCEP is unavailable")
    class WhenViaCepIsUnavailable {

        @Test
        @DisplayName("Should return address from Brasil API")
        void shouldReturnAddressFromBrasilApi() {
            AddressLookupResponse expected = expectedAddressResponse();

            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new AddressProviderUnavailableException("Via CEP indisponível."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenReturn(expected);

            AddressLookupResponse result = addressLookupService.findByPostalCode(POSTAL_CODE);

            assertEquals(expected, result);
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    @Nested
    @DisplayName("When no provider finds postal code")
    class WhenNoProviderFindsPostalCode {

        @Test
        @DisplayName("Should throw PostalCodeNotFoundException")
        void shouldThrowPostalCodeNotFoundException() {
            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new PostalCodeNotFoundException("CEP não encontrado no ViaCEP."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenThrow(new PostalCodeNotFoundException("CEP não encontrado na Brasil API"));

            PostalCodeNotFoundException exception = assertThrows(PostalCodeNotFoundException.class, () -> addressLookupService.findByPostalCode(POSTAL_CODE));

            assertEquals("CEP " + POSTAL_CODE + " não encontrado em nenhum provedor.", exception.getMessage());
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    @Nested
    @DisplayName("When all providers are unavailable")
    class WhenAllProvidersAreUnavailable {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new AddressProviderUnavailableException("Via CEP indisponível."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenThrow(new AddressProviderUnavailableException("BrasilAPI indisponível."));

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class, () -> addressLookupService.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    @Nested
    @DisplayName("When one provider does not find postal code and the other one is unavailable")
    class WhenOneProviderDoesNotFindPostalCodeAndTheOtherIsUnavailable {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new PostalCodeNotFoundException("CEP não encontrado no ViaCEP."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenThrow(new AddressProviderUnavailableException("BrasilAPI indisponível."));

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class, () -> addressLookupService.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    @Nested
    @DisplayName("When ViaCEP is unavailable and Brasil API does not find postal code")
    class WhenViaCepIsUnavailableAndBrasilApiDoesNotFindPostalCode {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            when(viaCepClient.findByPostalCode(POSTAL_CODE)).thenThrow(new AddressProviderUnavailableException("ViaCEP indisponível."));

            when(brasilApiClient.findByPostalCode(POSTAL_CODE)).thenThrow(new PostalCodeNotFoundException("CEP não encontrado na Brasil API"));

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class, () -> addressLookupService.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            verify(viaCepClient).findByPostalCode(POSTAL_CODE);
            verify(brasilApiClient).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(viaCepClient, brasilApiClient);
        }
    }

    private static AddressLookupResponse expectedAddressResponse() {
        return new AddressLookupResponse("Praça da Sé", "Sé", "São Paulo", "SP", AddressLookupServiceTest.POSTAL_CODE);
    }
}
