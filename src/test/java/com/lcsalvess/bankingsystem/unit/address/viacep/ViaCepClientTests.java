package com.lcsalvess.bankingsystem.unit.address.viacep;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.viacep.ViaCepClient;
import com.lcsalvess.bankingsystem.unit.address.FakeAddressServer;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.integration.address.validation.AddressLookupResponseValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ViaCepClientTests {

    private static final String POSTAL_CODE = "01001000";

    private static final String EXPECTED_REQUEST = "GET /ws/01001000/json/";

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final String VALID_RESPONSE = """
            {
              "cep": "01001-000",
              "logradouro": "Praça da Sé",
              "bairro": "Sé",
              "localidade": "São Paulo",
              "uf": "SP"
            }
            """;

    private FakeAddressServer server;

    private ViaCepClient viaCepClient;

    @BeforeEach
    void setUp() {
        server = new FakeAddressServer();
        viaCepClient = createClient(server.baseUrl(), FakeAddressServer.defaultRequestFactory());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Nested
    @DisplayName("When ViaCEP finds postal code")
    class WhenViaCepFindsPostalCode {

        @Test
        @DisplayName("Should return address with the postal code without hyphen")
        void shouldReturnAddressWithPostalCodeWithoutHyphen() {
            server.respondWith(200, VALID_RESPONSE);

            AddressLookupResponse result = viaCepClient.findByPostalCode(POSTAL_CODE);

            assertEquals(expectedAddressResponse(), result);
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }

        @Test
        @DisplayName("Should ignore response fields it does not map")
        void shouldIgnoreResponseFieldsItDoesNotMap() {
            server.respondWith(200, """
                    {
                      "cep": "01001-000",
                      "logradouro": "Praça da Sé",
                      "complemento": "lado ímpar",
                      "unidade": "",
                      "bairro": "Sé",
                      "localidade": "São Paulo",
                      "uf": "SP",
                      "estado": "São Paulo",
                      "regiao": "Sudeste",
                      "ibge": "3550308",
                      "gia": "1004",
                      "ddd": "11",
                      "siafi": "7107"
                    }
                    """);

            AddressLookupResponse result = viaCepClient.findByPostalCode(POSTAL_CODE);

            assertEquals(expectedAddressResponse(), result);
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP does not find postal code")
    class WhenViaCepDoesNotFindPostalCode {

        @Test
        @DisplayName("Should throw PostalCodeNotFoundException")
        void shouldThrowPostalCodeNotFoundException() {
            server.respondWith(200, """
                    { "erro": true }
                    """);

            PostalCodeNotFoundException exception = assertThrows(PostalCodeNotFoundException.class,
                    () -> viaCepClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.POSTAL_CODE_NOT_FOUND, exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP returns an empty body")
    class WhenViaCepReturnsAnEmptyBody {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, "");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> viaCepClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_EMPTY_RESPONSE, exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP returns an invalid address")
    class WhenViaCepReturnsAnInvalidAddress {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAddressResponses")
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException(String scenario, String response, String expectedError) {
            server.respondWith(200, response);

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> viaCepClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_INVALID_RESPONSE + expectedError, exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }

        static Stream<Arguments> invalidAddressResponses() {
            return Stream.of(
                    arguments("blank street", """
                            { "cep": "01001-000", "logradouro": "", "bairro": "Sé", "localidade": "São Paulo", "uf": "SP" }
                            """, "O logradouro é obrigatório."),
                    arguments("missing neighborhood", """
                            { "cep": "01001-000", "logradouro": "Praça da Sé", "localidade": "São Paulo", "uf": "SP" }
                            """, "O bairro é obrigatório."),
                    arguments("blank city", """
                            { "cep": "01001-000", "logradouro": "Praça da Sé", "bairro": "Sé", "localidade": " ", "uf": "SP" }
                            """, "A cidade é obrigatória."),
                    arguments("missing state", """
                            { "cep": "01001-000", "logradouro": "Praça da Sé", "bairro": "Sé", "localidade": "São Paulo" }
                            """, "O estado é obrigatório."),
                    arguments("unknown state", """
                            { "cep": "01001-000", "logradouro": "Praça da Sé", "bairro": "Sé", "localidade": "São Paulo", "uf": "XX" }
                            """, "O Estado deve ser uma UF válida."),
                    arguments("missing postal code", """
                            { "logradouro": "Praça da Sé", "bairro": "Sé", "localidade": "São Paulo", "uf": "SP" }
                            """, "O CEP é obrigatório."),
                    arguments("postal code with less than 8 digits", """
                            { "cep": "01001-00", "logradouro": "Praça da Sé", "bairro": "Sé", "localidade": "São Paulo", "uf": "SP" }
                            """, "O CEP deve conter exatamente 8 dígitos.")
            );
        }
    }

    @Nested
    @DisplayName("When ViaCEP responds with an error status")
    class WhenViaCepRespondsWithAnErrorStatus {

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {400, 500, 503})
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException(int status) {
            server.respondWith(status, "");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> viaCepClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE, exception.getMessage());
            assertInstanceOf(RestClientException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP returns a body that is not valid JSON")
    class WhenViaCepReturnsABodyThatIsNotValidJson {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, "<html>Bad Gateway</html>");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> viaCepClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE, exception.getMessage());
            assertInstanceOf(RestClientException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP takes longer than the read timeout")
    class WhenViaCepTakesLongerThanTheReadTimeout {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, VALID_RESPONSE, Duration.ofSeconds(1));
            ViaCepClient slowResponseClient = createClient(server.baseUrl(), FakeAddressServer.shortTimeoutRequestFactory());

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> slowResponseClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE, exception.getMessage());
            assertInstanceOf(ResourceAccessException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When ViaCEP is unreachable")
    class WhenViaCepIsUnreachable {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            ViaCepClient unreachableClient = createClient(FakeAddressServer.closedPortBaseUrl(), FakeAddressServer.defaultRequestFactory());

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> unreachableClient.findByPostalCode(POSTAL_CODE));

            assertEquals(ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE, exception.getMessage());
            assertInstanceOf(ResourceAccessException.class, exception.getCause());
            assertTrue(server.requests().isEmpty());
        }
    }

    private static ViaCepClient createClient(String baseUrl, JdkClientHttpRequestFactory requestFactory) {
        return new ViaCepClient(
                RestClient.builder(),
                requestFactory,
                baseUrl,
                new AddressLookupResponseValidator(VALIDATOR)
        );
    }

    private static AddressLookupResponse expectedAddressResponse() {
        return new AddressLookupResponse("Praça da Sé", "Sé", "São Paulo", "SP", POSTAL_CODE);
    }
}