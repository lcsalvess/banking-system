package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.TransactionExamplesCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.CLIENTS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.DEPOSIT_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.TRANSFER_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.WITHDRAW_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.createdJsonResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.exampleOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.jsonOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.openApiWith;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operationWithResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link TransactionExamplesCustomizer} directly, without a Spring context,
 * on hand-built {@link OpenAPI} documents whose responses already expose
 * {@code application/json}.
 */
@DisplayName("TransactionExamplesCustomizer")
class TransactionExamplesCustomizerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.openapi.TransactionExamplesCustomizerTests#";

    private final TransactionExamplesCustomizer customizer = new TransactionExamplesCustomizer();

    @Nested
    @DisplayName("Transaction examples")
    class TransactionExamplesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "transactionExamples")
        @DisplayName("Should set the example that matches the endpoint")
        void shouldSetTheExampleThatMatchesTheEndpoint(
                String path,
                String type,
                List<String> expectedKeys
        ) {
            Operation operation = operationWithResponse("201", createdJsonResponse());

            apply(openApiWith(path, new PathItem().post(operation)));

            Map<?, ?> example = exampleOf(jsonOf(responseOf(operation, "201")));

            assertEquals(expectedKeys, new ArrayList<>(example.keySet()));
            assertEquals("108cf2a1-bcee-460a-a5db-f9b231947411", example.get("transactionCode"));
            assertEquals(type, example.get("type"));
            assertEquals(450.95, example.get("amount"));
            assertEquals("2026-10-15T14:30:00", example.get("createdAt"));
        }

        @Test
        @DisplayName("Should include the transfer code only in the transfer example")
        void shouldIncludeTheTransferCodeOnlyInTheTransferExample() {
            Operation deposit = operationWithResponse("201", createdJsonResponse());
            Operation transfer = operationWithResponse("201", createdJsonResponse());
            OpenAPI openApi = new OpenAPI().paths(new Paths()
                    .addPathItem(DEPOSIT_PATH, new PathItem().post(deposit))
                    .addPathItem(TRANSFER_PATH, new PathItem().post(transfer)));

            apply(openApi);

            Map<?, ?> depositExample = exampleOf(jsonOf(responseOf(deposit, "201")));
            Map<?, ?> transferExample = exampleOf(jsonOf(responseOf(transfer, "201")));

            assertFalse(depositExample.containsKey("transferCode"));
            assertEquals(
                    "a12bc34d-5678-4abc-9def-1234567890ab",
                    transferExample.get("transferCode")
            );
        }

        @Test
        @DisplayName("Should not set an example on other POST endpoints")
        void shouldNotSetAnExampleOnOtherPostEndpoints() {
            Operation operation = operationWithResponse("201", createdJsonResponse());

            apply(openApiWith(CLIENTS_PATH, new PathItem().post(operation)));

            assertNull(jsonOf(responseOf(operation, "201")).getExample());
        }

        @Test
        @DisplayName("Should not set an example on non-POST operations of a transaction path")
        void shouldNotSetAnExampleOnNonPostOperations() {
            Operation operation = operationWithResponse("201", createdJsonResponse());

            apply(openApiWith(DEPOSIT_PATH, new PathItem().get(operation)));

            assertNull(jsonOf(responseOf(operation, "201")).getExample());
        }

        @Test
        @DisplayName("Should not set an example when there is no 201 response")
        void shouldNotSetAnExampleWithoutA201Response() {
            Operation operation = operationWithResponse("200", createdJsonResponse());

            apply(openApiWith(WITHDRAW_PATH, new PathItem().post(operation)));

            assertNull(jsonOf(responseOf(operation, "200")).getExample());
            assertNull(operation.getResponses().get("201"));
        }

        @Test
        @DisplayName("Should not fail when the 201 response has no content")
        void shouldNotFailWhenThe201ResponseHasNoContent() {
            Operation operation = operationWithResponse(
                    "201",
                    new ApiResponse().description("Created.")
            );

            assertDoesNotThrow(() ->
                    apply(openApiWith(DEPOSIT_PATH, new PathItem().post(operation))));

            assertNull(responseOf(operation, "201").getContent());
        }

        @Test
        @DisplayName("Should not fail when the 201 response has empty content")
        void shouldNotFailWhenThe201ResponseHasEmptyContent() {
            Operation operation = operationWithResponse(
                    "201",
                    new ApiResponse().description("Created.").content(new Content())
            );

            assertDoesNotThrow(() ->
                    apply(openApiWith(DEPOSIT_PATH, new PathItem().post(operation))));

            assertTrue(responseOf(operation, "201").getContent().isEmpty());
        }
    }
    @SuppressWarnings("unused")
    static Stream<Arguments> transactionExamples() {
        return Stream.of(
                Arguments.of(
                        DEPOSIT_PATH,
                        "DEPOSIT",
                        List.of("transactionCode", "type", "amount", "createdAt")
                ),
                Arguments.of(
                        WITHDRAW_PATH,
                        "WITHDRAWAL",
                        List.of("transactionCode", "type", "amount", "createdAt")
                ),
                Arguments.of(
                        TRANSFER_PATH,
                        "TRANSFER_SENT",
                        List.of("transactionCode", "transferCode", "type", "amount", "createdAt")
                )
        );
    }

    private void apply(OpenAPI openApi) {
        customizer.customise(openApi);
    }
}
