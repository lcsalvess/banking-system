package com.lcsalvess.bankingsystem.unit.service.address;

import com.lcsalvess.bankingsystem.dto.request.AddressRequestDTO;
import com.lcsalvess.bankingsystem.entity.Address;
import com.lcsalvess.bankingsystem.entity.enums.State;
import com.lcsalvess.bankingsystem.integration.address.AddressLookupService;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.service.address.AddressData;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTests {

    private static final String POSTAL_CODE = "01001000";

    @Mock
    private AddressLookupService addressLookupService;

    @InjectMocks
    private AddressService addressService;

    @Nested
    @DisplayName("When creating an address from postal code")
    class CreateFromPostalCode {

        @Test
        @DisplayName("Should create address combining provider data with street number and complement")
        void shouldCreateAddressCombiningProviderDataWithStreetNumberAndComplement() {
            AddressRequestDTO dto = new AddressRequestDTO("123", "Apto 45", POSTAL_CODE);

            when(addressLookupService.findByPostalCode(POSTAL_CODE)).thenReturn(lookupResponse());

            Address result = addressService.createFromPostalCode(dto);

            assertNull(result.getId());
            assertEquals("Praça da Sé", result.getStreetName());
            assertEquals("123", result.getStreetNumber());
            assertEquals("Apto 45", result.getComplement());
            assertEquals("Sé", result.getNeighborhood());
            assertEquals("São Paulo", result.getCity());
            assertEquals(State.SP, result.getState());
            assertEquals(POSTAL_CODE, result.getPostalCode());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }

        @Test
        @DisplayName("Should create address without complement when it is not informed")
        void shouldCreateAddressWithoutComplementWhenItIsNotInformed() {
            AddressRequestDTO dto = new AddressRequestDTO("123", null, POSTAL_CODE);

            when(addressLookupService.findByPostalCode(POSTAL_CODE)).thenReturn(lookupResponse());

            Address result = addressService.createFromPostalCode(dto);

            assertNull(result.getComplement());
            assertEquals("123", result.getStreetNumber());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }

        @Test
        @DisplayName("Should throw PostalCodeNotFoundException when postal code is not found")
        void shouldThrowPostalCodeNotFoundExceptionWhenPostalCodeIsNotFound() {
            AddressRequestDTO dto = new AddressRequestDTO("123", null, POSTAL_CODE);

            when(addressLookupService.findByPostalCode(POSTAL_CODE))
                    .thenThrow(new PostalCodeNotFoundException("CEP " + POSTAL_CODE + " não encontrado em nenhum provedor."));

            PostalCodeNotFoundException exception = assertThrows(PostalCodeNotFoundException.class,
                    () -> addressService.createFromPostalCode(dto));

            assertEquals("CEP " + POSTAL_CODE + " não encontrado em nenhum provedor.", exception.getMessage());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException when providers are unavailable")
        void shouldThrowAddressProviderUnavailableExceptionWhenProvidersAreUnavailable() {
            AddressRequestDTO dto = new AddressRequestDTO("123", null, POSTAL_CODE);

            when(addressLookupService.findByPostalCode(POSTAL_CODE))
                    .thenThrow(new AddressProviderUnavailableException("Serviços de CEP indisponíveis no momento."));

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> addressService.createFromPostalCode(dto));

            assertEquals("Serviços de CEP indisponíveis no momento.", exception.getMessage());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }
    }

    @Nested
    @DisplayName("When finding an address by postal code")
    class FindAddressByPostalCode {

        @Test
        @DisplayName("Should return address data with the state converted to enum")
        void shouldReturnAddressDataWithTheStateConvertedToEnum() {
            when(addressLookupService.findByPostalCode(POSTAL_CODE)).thenReturn(lookupResponse());

            AddressData result = addressService.findAddressByPostalCode(POSTAL_CODE);

            assertEquals(new AddressData("Praça da Sé", "Sé", "São Paulo", State.SP, POSTAL_CODE), result);

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }

        @Test
        @DisplayName("Should throw PostalCodeNotFoundException when postal code is not found")
        void shouldThrowPostalCodeNotFoundExceptionWhenPostalCodeIsNotFound() {
            when(addressLookupService.findByPostalCode(POSTAL_CODE))
                    .thenThrow(new PostalCodeNotFoundException("CEP " + POSTAL_CODE + " não encontrado em nenhum provedor."));

            PostalCodeNotFoundException exception = assertThrows(PostalCodeNotFoundException.class,
                    () -> addressService.findAddressByPostalCode(POSTAL_CODE));

            assertEquals("CEP " + POSTAL_CODE + " não encontrado em nenhum provedor.", exception.getMessage());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException when providers are unavailable")
        void shouldThrowAddressProviderUnavailableExceptionWhenProvidersAreUnavailable() {
            when(addressLookupService.findByPostalCode(POSTAL_CODE))
                    .thenThrow(new AddressProviderUnavailableException("Serviços de CEP indisponíveis no momento."));

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> addressService.findAddressByPostalCode(POSTAL_CODE));

            assertEquals("Serviços de CEP indisponíveis no momento.", exception.getMessage());

            verify(addressLookupService).findByPostalCode(POSTAL_CODE);
            verifyNoMoreInteractions(addressLookupService);
        }
    }

    private static AddressLookupResponse lookupResponse() {
        return new AddressLookupResponse("Praça da Sé", "Sé", "São Paulo", "SP", POSTAL_CODE);
    }
}