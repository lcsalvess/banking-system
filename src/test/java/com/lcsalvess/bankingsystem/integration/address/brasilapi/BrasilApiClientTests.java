package com.lcsalvess.bankingsystem.integration.address.brasilapi;

import com.lcsalvess.bankingsystem.integration.address.FakeAddressServer;
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

class BrasilApiClientTests {

    private static final String POSTAL_CODE = "01001000";

    private static final String EXPECTED_REQUEST = "GET /api/cep/v1/01001000";

    private static final String UNAVAILABLE_MESSAGE = "Não foi possível consultar a Brasil API.";

    private static final String INVALID_ADDRESS_MESSAGE = "O provedor retornou um endereço inválido: ";

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final String VALID_RESPONSE = """
            {
              "cep": "01001000",
              "state": "SP",
              "city": "São Paulo",
              "neighborhood": "Sé",
              "street": "Praça da Sé"
            }
            """;

    private FakeAddressServer server;

    private BrasilApiClient brasilApiClient;

    @BeforeEach
    void setUp() {
        server = new FakeAddressServer();
        brasilApiClient = createClient(server.baseUrl(), FakeAddressServer.defaultRequestFactory());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Nested
    @DisplayName("When Brasil API finds postal code")
    class WhenBrasilApiFindsPostalCode {

        @Test
        @DisplayName("Should return address")
        void shouldReturnAddress() {
            server.respondWith(200, VALID_RESPONSE);

            AddressLookupResponse result = brasilApiClient.findByPostalCode(POSTAL_CODE);

            assertEquals(expectedAddressResponse(), result);
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }

        @Test
        @DisplayName("Should remove hyphen from the postal code when the provider sends it")
        void shouldRemoveHyphenFromPostalCode() {
            server.respondWith(200, """
                    { "cep": "01001-000", "state": "SP", "city": "São Paulo", "neighborhood": "Sé", "street": "Praça da Sé" }
                    """);

            AddressLookupResponse result = brasilApiClient.findByPostalCode(POSTAL_CODE);

            assertEquals(expectedAddressResponse(), result);
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }

        @Test
        @DisplayName("Should ignore response fields it does not map")
        void shouldIgnoreResponseFieldsItDoesNotMap() {
            server.respondWith(200, """
                    {
                      "cep": "01001000",
                      "state": "SP",
                      "city": "São Paulo",
                      "neighborhood": "Sé",
                      "street": "Praça da Sé",
                      "service": "open-cep",
                      "location": {
                        "type": "Point",
                        "coordinates": {
                          "longitude": "-46.6333",
                          "latitude": "-23.5505"
                        }
                      }
                    }
                    """);

            AddressLookupResponse result = brasilApiClient.findByPostalCode(POSTAL_CODE);

            assertEquals(expectedAddressResponse(), result);
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API does not find postal code")
    class WhenBrasilApiDoesNotFindPostalCode {

        @Test
        @DisplayName("Should throw PostalCodeNotFoundException")
        void shouldThrowPostalCodeNotFoundException() {
            server.respondWith(404, """
                    {
                      "name": "CepPromiseError",
                      "message": "Todos os serviços de CEP retornaram erro.",
                      "type": "service_error"
                    }
                    """);

            PostalCodeNotFoundException exception = assertThrows(PostalCodeNotFoundException.class,
                    () -> brasilApiClient.findByPostalCode(POSTAL_CODE));

            assertEquals("CEP não encontrado: " + POSTAL_CODE, exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API returns an empty body")
    class WhenBrasilApiReturnsAnEmptyBody {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, "");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> brasilApiClient.findByPostalCode(POSTAL_CODE));

            assertEquals("A Brasil API retornou uma resposta vazia.", exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API returns an invalid address")
    class WhenBrasilApiReturnsAnInvalidAddress {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidAddressResponses")
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException(String scenario, String response, String expectedError) {
            server.respondWith(200, response);

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> brasilApiClient.findByPostalCode(POSTAL_CODE));

            assertEquals(INVALID_ADDRESS_MESSAGE + expectedError, exception.getMessage());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }

        static Stream<Arguments> invalidAddressResponses() {
            return Stream.of(
                    arguments("blank street", """
                            { "cep": "01001000", "state": "SP", "city": "São Paulo", "neighborhood": "Sé", "street": "" }
                            """, "O logradouro é obrigatório."),
                    arguments("missing neighborhood", """
                            { "cep": "01001000", "state": "SP", "city": "São Paulo", "street": "Praça da Sé" }
                            """, "O bairro é obrigatório."),
                    arguments("blank city", """
                            { "cep": "01001000", "state": "SP", "city": " ", "neighborhood": "Sé", "street": "Praça da Sé" }
                            """, "A cidade é obrigatória."),
                    arguments("missing state", """
                            { "cep": "01001000", "city": "São Paulo", "neighborhood": "Sé", "street": "Praça da Sé" }
                            """, "O estado é obrigatório."),
                    arguments("unknown state", """
                            { "cep": "01001000", "state": "XX", "city": "São Paulo", "neighborhood": "Sé", "street": "Praça da Sé" }
                            """, "O Estado deve ser uma UF válida."),
                    arguments("missing postal code", """
                            { "state": "SP", "city": "São Paulo", "neighborhood": "Sé", "street": "Praça da Sé" }
                            """, "O CEP é obrigatório."),
                    arguments("postal code with less than 8 digits", """
                            { "cep": "0100100", "state": "SP", "city": "São Paulo", "neighborhood": "Sé", "street": "Praça da Sé" }
                            """, "O CEP deve conter exatamente 8 dígitos.")
            );
        }
    }

    @Nested
    @DisplayName("When Brasil API responds with an error status other than not found")
    class WhenBrasilApiRespondsWithAnErrorStatus {

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {400, 500, 503})
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException(int status) {
            server.respondWith(status, "");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> brasilApiClient.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            assertInstanceOf(RestClientException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API returns a body that is not valid JSON")
    class WhenBrasilApiReturnsABodyThatIsNotValidJson {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, "<html>Bad Gateway</html>");

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> brasilApiClient.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            assertInstanceOf(RestClientException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API takes longer than the read timeout")
    class WhenBrasilApiTakesLongerThanTheReadTimeout {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            server.respondWith(200, VALID_RESPONSE, Duration.ofSeconds(1));
            BrasilApiClient slowResponseClient = createClient(server.baseUrl(), FakeAddressServer.shortTimeoutRequestFactory());

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> slowResponseClient.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            assertInstanceOf(ResourceAccessException.class, exception.getCause());
            assertEquals(List.of(EXPECTED_REQUEST), server.requests());
        }
    }

    @Nested
    @DisplayName("When Brasil API is unreachable")
    class WhenBrasilApiIsUnreachable {

        @Test
        @DisplayName("Should throw AddressProviderUnavailableException")
        void shouldThrowAddressProviderUnavailableException() {
            BrasilApiClient unreachableClient = createClient(FakeAddressServer.closedPortBaseUrl(), FakeAddressServer.defaultRequestFactory());

            AddressProviderUnavailableException exception = assertThrows(AddressProviderUnavailableException.class,
                    () -> unreachableClient.findByPostalCode(POSTAL_CODE));

            assertEquals(UNAVAILABLE_MESSAGE, exception.getMessage());
            assertInstanceOf(ResourceAccessException.class, exception.getCause());
            assertTrue(server.requests().isEmpty());
        }
    }

    private static BrasilApiClient createClient(String baseUrl, JdkClientHttpRequestFactory requestFactory) {
        return new BrasilApiClient(
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