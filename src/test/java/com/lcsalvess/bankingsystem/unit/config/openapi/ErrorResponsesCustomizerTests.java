package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.ErrorResponsesCustomizer;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.CLIENTS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.ERROR_SCHEMA_NAME;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.ERROR_SCHEMA_REF;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.LOGIN_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.exampleOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.jsonOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.jsonResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.openApiWith;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operation;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operationWithResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ErrorResponsesCustomizer} directly, without a Spring context,
 * on hand-built {@link OpenAPI} documents.
 */
@DisplayName("ErrorResponsesCustomizer")
class ErrorResponsesCustomizerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.openapi.ErrorResponsesCustomizerTests#";

    private final ErrorResponsesCustomizer customizer = new ErrorResponsesCustomizer();

    @Nested
    @DisplayName("Error schema registration")
    class ErrorSchemaRegistrationTests {

        @Test
        @DisplayName("Should create the components when the document has none")
        void shouldCreateComponentsWhenAbsent() {
            OpenAPI openApi = openApiWith(CLIENTS_PATH, new PathItem().get(operation()));

            apply(openApi);

            assertNotNull(openApi.getComponents());
            assertNotNull(openApi.getComponents().getSchemas());
            assertTrue(openApi.getComponents().getSchemas().containsKey(ERROR_SCHEMA_NAME));
        }

        @Test
        @DisplayName("Should create the schemas map when the components have none")
        void shouldCreateSchemasWhenAbsent() {
            OpenAPI openApi = openApiWith(CLIENTS_PATH, new PathItem().get(operation()));
            openApi.setComponents(new Components());

            apply(openApi);

            assertNotNull(openApi.getComponents().getSchemas());
            assertTrue(openApi.getComponents().getSchemas().containsKey(ERROR_SCHEMA_NAME));
        }

        @Test
        @DisplayName("Should register the ApiErrorResponse fields")
        void shouldRegisterTheApiErrorResponseFields() {
            OpenAPI openApi = openApiWith(CLIENTS_PATH, new PathItem().get(operation()));

            apply(openApi);

            Schema<?> schema = openApi.getComponents().getSchemas().get(ERROR_SCHEMA_NAME);

            assertNotNull(schema);
            assertEquals(
                    Set.of("status", "message", "timestamp", "errors"),
                    schema.getProperties().keySet()
            );
        }

        @Test
        @DisplayName("Should not replace an ApiErrorResponse schema that already exists")
        void shouldNotReplaceAnExistingSchema() {
            Schema<?> existing = new ObjectSchema().description("existing");
            OpenAPI openApi = openApiWith(CLIENTS_PATH, new PathItem().get(operation()));
            openApi.setComponents(new Components().addSchemas(ERROR_SCHEMA_NAME, existing));

            apply(openApi);

            assertSame(existing, openApi.getComponents().getSchemas().get(ERROR_SCHEMA_NAME));
        }

        @Test
        @DisplayName("Should keep the other schemas already registered")
        void shouldKeepOtherSchemas() {
            Schema<?> other = new ObjectSchema().description("other");
            OpenAPI openApi = openApiWith(CLIENTS_PATH, new PathItem().get(operation()));
            openApi.setComponents(new Components().addSchemas("Other", other));

            apply(openApi);

            assertSame(other, openApi.getComponents().getSchemas().get("Other"));
            assertTrue(openApi.getComponents().getSchemas().containsKey(ERROR_SCHEMA_NAME));
        }
    }

    @Nested
    @DisplayName("Authentication responses (401 and 403)")
    class AuthenticationResponsesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "authenticationResponses")
        @DisplayName("Should add the response to protected operations")
        void shouldAddToProtectedOperations(String status, String description) {
            Operation operation = operation();

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            ApiResponse response = responseOf(operation, status);

            assertEquals(description, response.getDescription());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }

        @Test
        @DisplayName("Should not add 401 or 403 to POST /api/v1/auth/login")
        void shouldNotAddToPublicLogin() {
            Operation login = operation();

            apply(openApiWith(LOGIN_PATH, new PathItem().post(login)));

            assertNull(login.getResponses().get("401"));
            assertNull(login.getResponses().get("403"));
        }

        @Test
        @DisplayName("Should treat only POST on the login path as public")
        void shouldTreatOnlyPostOnLoginAsPublic() {
            Operation post = operation();
            Operation get = operation();

            apply(openApiWith(LOGIN_PATH, new PathItem().post(post).get(get)));

            assertNull(post.getResponses().get("401"));
            assertNull(post.getResponses().get("403"));
            assertNotNull(get.getResponses().get("401"));
            assertNotNull(get.getResponses().get("403"));
        }

        @Test
        @DisplayName("Should keep a 401 documented on the login operation")
        void shouldKeepDocumentedLoginResponse() {
            Operation login = operationWithResponse(
                    "401",
                    new ApiResponse().description("Invalid credentials.")
            );

            apply(openApiWith(LOGIN_PATH, new PathItem().post(login)));

            ApiResponse response = responseOf(login, "401");

            assertEquals("Invalid credentials.", response.getDescription());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());

            Map<?, ?> example = exampleOf(jsonOf(response));

            assertEquals(
                    ApiErrorMessages.INVALID_CREDENTIALS,
                    example.get("message")
            );

            assertNull(login.getResponses().get("403"));
        }
    }

    @Nested
    @DisplayName("Common responses (405, 415, 500 and 503)")
    class CommonResponsesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "commonResponses")
        @DisplayName("Should add the response to protected operations")
        void shouldAddToProtectedOperations(String status, String description) {
            Operation operation = operation();

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            ApiResponse response = responseOf(operation, status);

            assertEquals(description, response.getDescription());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "commonResponses")
        @DisplayName("Should add the response to POST /api/v1/auth/login")
        void shouldAddToPublicLogin(String status, String description) {
            Operation login = operation();

            apply(openApiWith(LOGIN_PATH, new PathItem().post(login)));

            ApiResponse response = responseOf(login, status);

            assertEquals(description, response.getDescription());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }
    }

    @Nested
    @DisplayName("Documented responses")
    class DocumentedResponsesTests {

        @Test
        @DisplayName("Should keep the description of a documented response")
        void shouldKeepTheDescription() {
            Operation operation = operationWithResponse(
                    "404",
                    new ApiResponse().description("Client not found.")
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            assertEquals("Client not found.", responseOf(operation, "404").getDescription());
        }

        @Test
        @DisplayName("Should keep the description of a documented 401 without content")
        void shouldKeepTheDescriptionOfADocumentedAuthenticationResponse() {
            Operation operation = operationWithResponse(
                    "401",
                    new ApiResponse().description("Custom unauthorized.")
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            ApiResponse response = responseOf(operation, "401");

            assertEquals("Custom unauthorized.", response.getDescription());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }

        @Test
        @DisplayName("Should give the error schema to a documented response whose content is empty")
        void shouldGiveTheErrorSchemaToADocumentedResponseWithEmptyContent() {
            Operation operation = operationWithResponse(
                    "403",
                    new ApiResponse().description("Custom forbidden.").content(new Content())
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            ApiResponse response = responseOf(operation, "403");

            assertEquals("Custom forbidden.", response.getDescription());
            assertEquals(1, response.getContent().size());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }

        @Test
        @DisplayName("Should keep the description of a documented response that already has content")
        void shouldKeepTheDescriptionOfADocumentedResponseWithContent() {
            Operation operation = operationWithResponse(
                    "500",
                    jsonResponse("Custom server error.", new StringSchema())
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            ApiResponse response = responseOf(operation, "500");

            assertEquals("Custom server error.", response.getDescription());
            assertEquals(1, response.getContent().size());
            assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
        }

        @Test
        @DisplayName("Should replace the content of 4xx and 5xx responses with the error schema")
        void shouldReplaceErrorContent() {
            Operation operation = operation();
            operation.getResponses().addApiResponse(
                    "400",
                    jsonResponse("Bad request.", new StringSchema())
            );
            operation.getResponses().addApiResponse(
                    "502",
                    jsonResponse("Bad gateway.", new StringSchema())
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().post(operation)));

            for (String status : List.of("400", "502")) {
                ApiResponse response = responseOf(operation, status);

                assertEquals(1, response.getContent().size());
                assertEquals(ERROR_SCHEMA_REF, jsonOf(response).getSchema().get$ref());
            }
        }

        @Test
        @DisplayName("Should not change the default response")
        void shouldNotChangeTheDefaultResponse() {
            Operation operation = operationWithResponse(
                    "default",
                    new ApiResponse().description("Default.")
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            assertNull(responseOf(operation, "default").getContent());
        }

        @Test
        @DisplayName("Should not add error content to 2xx and 3xx responses")
        void shouldNotAddErrorContentToNonErrorResponses() {
            Operation operation = operation();
            operation.getResponses().addApiResponse("204", new ApiResponse().description("No content."));
            operation.getResponses().addApiResponse("302", new ApiResponse().description("Found."));

            apply(openApiWith(CLIENTS_PATH, new PathItem().delete(operation)));

            assertNull(responseOf(operation, "204").getContent());
            assertNull(responseOf(operation, "302").getContent());
        }
    }

    @Nested
    @DisplayName("Error examples")
    class ErrorExamplesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "errorMessages")
        @DisplayName("Should use the message that matches the status")
        void shouldUseTheMessageThatMatchesTheStatus(String status, String message) {
            Operation operation = operationWithResponse(
                    status,
                    new ApiResponse().description("Documented.")
            );

            apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

            MediaType json = jsonOf(responseOf(operation, status));
            Map<?, ?> example = exampleOf(json);

            assertEquals(ERROR_SCHEMA_REF, json.getSchema().get$ref());
            assertEquals(Set.of("status", "message", "timestamp"), example.keySet());
            assertEquals(Integer.parseInt(status), example.get("status"));
            assertEquals(message, example.get("message"));
            assertEquals("2026-10-15T18:30:00Z", example.get("timestamp"));
        }
    }
    @SuppressWarnings("unused")
    static Stream<Arguments> authenticationResponses() {
        return Stream.of(
                Arguments.of(
                        "401",
                        "Unauthorized - Authentication is required or the JWT is invalid."
                ),
                Arguments.of(
                        "403",
                        "Forbidden - Insufficient permissions to access this resource."
                )
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> commonResponses() {
        return Stream.of(
                Arguments.of(
                        "405",
                        "Method Not Allowed - The HTTP method is not supported."
                ),
                Arguments.of(
                        "415",
                        "Unsupported Media Type - The request content type is not supported."
                ),
                Arguments.of(
                        "500",
                        "Unexpected internal server error."
                ),
                Arguments.of(
                        "503",
                        "Service Unavailable - A required infrastructure dependency "
                                + "is temporarily unavailable."
                )
        );
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> errorMessages() {
        return Stream.of(
                Arguments.of("400", "Request validation failed."),
                Arguments.of("401", "Authentication is required or the JWT is invalid."),
                Arguments.of("403", "Insufficient permissions to access this resource."),
                Arguments.of("404", "The requested resource was not found."),
                Arguments.of("405", "The HTTP method is not supported."),
                Arguments.of("409", "The request conflicts with the current resource state."),
                Arguments.of("415", "The request content type is not supported."),
                Arguments.of("429", "Too many login attempts. Please wait before trying again."),
                Arguments.of("500", "Unexpected internal server error."),
                Arguments.of("503", "Service temporarily unavailable."),
                Arguments.of("422", "Unexpected internal server error.")
        );
    }

    private void apply(OpenAPI openApi) {
        customizer.customise(openApi);
    }
}
