package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.OpenApiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link OpenApiConfig} directly, without a Spring context.
 * The {@code globalResponses} customizer is applied to hand-built
 * {@link OpenAPI} documents, so each rule can be checked in isolation.
 */
@DisplayName("OpenApiConfig")
class OpenApiConfigTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiConfigTests#";

    private static final String JSON = "application/json";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String CLIENTS_PATH = "/api/v1/clients";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String AUDIT_EVENTS_PATH = "/api/v1/audit-events";
    private static final String DEPOSIT_PATH = "/api/v1/transactions/deposit";
    private static final String WITHDRAW_PATH = "/api/v1/transactions/withdraw";
    private static final String TRANSFER_PATH = "/api/v1/transactions/transfer";

    private static final String ERROR_SCHEMA_NAME = "ApiErrorResponse";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiErrorResponse";
    private static final String PAGE_METADATA_REF = "#/components/schemas/PageMetadata";
    private static final String PAGE_RESPONSE_REF = "#/components/schemas/PageResponse";

    private final OpenApiConfig config = new OpenApiConfig();

    @Nested
    @DisplayName("bankingSystemOpenAPI")
    class BankingSystemOpenApiTests {

        private final OpenAPI openApi = config.bankingSystemOpenAPI();

        @Test
        @DisplayName("Should describe the API")
        void shouldDescribeTheApi() {
            Info info = openApi.getInfo();

            assertNotNull(info);
            assertEquals("Banking System API", info.getTitle());
            assertEquals("1.0.0", info.getVersion());
            assertTrue(info.getDescription().contains("RESTful banking API"));
            assertTrue(info.getDescription().contains("login endpoint"));
            assertTrue(info.getDescription().contains("Authorize"));
        }

        @Test
        @DisplayName("Should link to the source code")
        void shouldLinkToTheSourceCode() {
            assertNotNull(openApi.getExternalDocs());
            assertEquals("Source code on GitHub", openApi.getExternalDocs().getDescription());
            assertEquals(
                    "https://github.com/lcsalvess/banking-system",
                    openApi.getExternalDocs().getUrl()
            );
        }

        @Test
        @DisplayName("Should require the bearer authentication scheme globally")
        void shouldRequireBearerAuthGlobally() {
            List<SecurityRequirement> security = openApi.getSecurity();

            assertNotNull(security);
            assertEquals(1, security.size());
            assertEquals(Set.of("bearerAuth"), security.getFirst().keySet());
            assertEquals(List.of(), security.getFirst().get("bearerAuth"));
        }

        @Test
        @DisplayName("Should declare the bearer JWT security scheme")
        void shouldDeclareTheBearerJwtScheme() {
            Map<String, SecurityScheme> schemes = openApi.getComponents().getSecuritySchemes();

            assertEquals(Set.of("bearerAuth"), schemes.keySet());

            SecurityScheme scheme = schemes.get("bearerAuth");

            assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
            assertEquals("bearer", scheme.getScheme());
            assertEquals("JWT", scheme.getBearerFormat());
        }

        @Test
        @DisplayName("Should reference a declared security scheme in the global requirement")
        void shouldReferenceADeclaredSecurityScheme() {
            String requiredScheme = openApi.getSecurity().getFirst().keySet().iterator().next();

            assertTrue(openApi.getComponents().getSecuritySchemes().containsKey(requiredScheme));
        }
    }

    @Nested
    @DisplayName("globalResponses")
    class GlobalResponsesTests {

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

        @Nested
        @DisplayName("Successful responses (200 and 201)")
        class SuccessfulResponsesTests {

            @ParameterizedTest(name = "{0}")
            @ValueSource(strings = {"200", "201"})
            @DisplayName("Should expose only application/json, keeping the original media type")
            void shouldExposeOnlyApplicationJson(String status) {
                MediaType first = new MediaType().schema(new StringSchema());
                MediaType second = new MediaType().schema(new IntegerSchema());
                Content content = new Content()
                        .addMediaType("*/*", first)
                        .addMediaType("text/plain", second);
                Operation operation = operationWithResponse(
                        status,
                        new ApiResponse().description("Success.").content(content)
                );

                apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

                Content result = responseOf(operation, status).getContent();

                assertEquals(Set.of(JSON), result.keySet());
                assertSame(first, result.get(JSON));
            }

            @Test
            @DisplayName("Should keep a 200 response whose content is empty")
            void shouldKeepEmptyContent() {
                Operation operation = operationWithResponse(
                        "200",
                        new ApiResponse().description("OK.").content(new Content())
                );

                apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

                assertTrue(responseOf(operation, "200").getContent().isEmpty());
            }

            @Test
            @DisplayName("Should keep a 200 response that has no content")
            void shouldKeepAbsentContent() {
                Operation operation = operationWithResponse(
                        "200",
                        new ApiResponse().description("OK.")
                );

                apply(openApiWith(CLIENTS_PATH, new PathItem().get(operation)));

                assertNull(responseOf(operation, "200").getContent());
            }

            @Test
            @DisplayName("Should keep the media types of other successful statuses")
            void shouldKeepOtherSuccessfulStatuses() {
                Content content = new Content().addMediaType("*/*", new MediaType());
                Operation operation = operationWithResponse(
                        "202",
                        new ApiResponse().description("Accepted.").content(content)
                );

                apply(openApiWith(CLIENTS_PATH, new PathItem().post(operation)));

                assertEquals(Set.of("*/*"), responseOf(operation, "202").getContent().keySet());
            }
        }

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
                Operation operation = operationWithResponse("201", createdResponse());

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
                Operation deposit = operationWithResponse("201", createdResponse());
                Operation transfer = operationWithResponse("201", createdResponse());
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
                Operation operation = operationWithResponse("201", createdResponse());

                apply(openApiWith(CLIENTS_PATH, new PathItem().post(operation)));

                assertNull(jsonOf(responseOf(operation, "201")).getExample());
            }

            @Test
            @DisplayName("Should not set an example on non-POST operations of a transaction path")
            void shouldNotSetAnExampleOnNonPostOperations() {
                Operation operation = operationWithResponse("201", createdResponse());

                apply(openApiWith(DEPOSIT_PATH, new PathItem().get(operation)));

                assertNull(jsonOf(responseOf(operation, "201")).getExample());
            }

            @Test
            @DisplayName("Should not set an example when there is no 201 response")
            void shouldNotSetAnExampleWithoutA201Response() {
                Operation operation = operationWithResponse("200", createdResponse());

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

        @Nested
        @DisplayName("PageMetadata schema")
        class PageMetadataTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource(PROVIDER + "pageMetadataProperties")
            @DisplayName("Should describe each property and set its example")
            void shouldDescribeEachProperty(String property, String description, Integer example) {
                OpenAPI openApi = emptyOpenApi();
                openApi.setComponents(
                        new Components().addSchemas("PageMetadata", pageMetadataSchema())
                );

                apply(openApi);

                Schema<?> pageMetadata = openApi.getComponents().getSchemas().get("PageMetadata");
                Schema<?> result = pageMetadata.getProperties().get(property);

                assertEquals(description, result.getDescription());
                assertEquals(example, result.getExample());
            }

            @Test
            @DisplayName("Should only customize the properties that exist")
            void shouldOnlyCustomizeExistingProperties() {
                ObjectSchema pageMetadata = new ObjectSchema();
                pageMetadata.addProperty("size", new IntegerSchema());
                OpenAPI openApi = emptyOpenApi();
                openApi.setComponents(new Components().addSchemas("PageMetadata", pageMetadata));

                apply(openApi);

                assertEquals(Set.of("size"), pageMetadata.getProperties().keySet());
                assertEquals(
                        "Number of elements per page. The default is endpoint-specific.",
                        pageMetadata.getProperties().get("size").getDescription()
                );
            }

            @Test
            @DisplayName("Should ignore the document when PageMetadata is not registered")
            void shouldIgnoreWhenPageMetadataIsNotRegistered() {
                OpenAPI openApi = emptyOpenApi();
                openApi.setComponents(new Components().addSchemas("Other", new ObjectSchema()));

                assertDoesNotThrow(() -> apply(openApi));
            }

            @Test
            @DisplayName("Should ignore a PageMetadata schema without properties")
            void shouldIgnoreWhenPageMetadataHasNoProperties() {
                ObjectSchema pageMetadata = new ObjectSchema();
                OpenAPI openApi = emptyOpenApi();
                openApi.setComponents(new Components().addSchemas("PageMetadata", pageMetadata));

                assertDoesNotThrow(() -> apply(openApi));
                assertNull(pageMetadata.getProperties());
            }
        }

        @Nested
        @DisplayName("Page examples")
        class PageExamplesTests {

            @ParameterizedTest(name = "{0}")
            @ValueSource(strings = {USERS_PATH, AUDIT_EVENTS_PATH})
            @DisplayName("Should add the page example to a copy of the response schema")
            void shouldAddThePageExampleToACopy(String path) {
                OpenAPI openApi = pageOpenApi(path);
                Schema<?> shared = openApi.getComponents().getSchemas().get("PageResponse");

                apply(openApi);

                Schema<?> responseSchema = responseSchemaOf(openApi, path);

                assertNull(responseSchema.get$ref());
                assertNotSame(shared, responseSchema);
                assertEquals("PageResponse", responseSchema.getTitle());
                assertEquals("Paginated response.", responseSchema.getDescription());
                assertEquals(List.of("content", "page"), responseSchema.getRequired());
                assertEquals(Set.of("content", "page"), responseSchema.getProperties().keySet());

                Schema<?> page = responseSchema.getProperties().get("page");

                assertInstanceOf(ObjectSchema.class, page);
                assertEquals(
                        Set.of("size", "number", "totalElements", "totalPages"),
                        page.getProperties().keySet()
                );
                assertEquals(
                        "Number of elements per page. The default is endpoint-specific.",
                        page.getProperties().get("size").getDescription()
                );

                Map<?, ?> example = assertInstanceOf(Map.class, page.getExample());

                assertEquals(
                        List.of("size", "number", "totalElements", "totalPages"),
                        new ArrayList<>(example.keySet())
                );
                assertEquals(
                        Map.of("size", 20, "number", 0, "totalElements", 10, "totalPages", 1),
                        example
                );
            }

            @ParameterizedTest(name = "{0}")
            @ValueSource(strings = {USERS_PATH, AUDIT_EVENTS_PATH})
            @DisplayName("Should not change the shared response schema")
            void shouldNotChangeTheSharedSchema(String path) {
                OpenAPI openApi = pageOpenApi(path);
                Schema<?> shared = openApi.getComponents().getSchemas().get("PageResponse");
                Schema<?> originalPage = shared.getProperties().get("page");

                apply(openApi);

                assertSame(shared, openApi.getComponents().getSchemas().get("PageResponse"));
                assertSame(originalPage, shared.getProperties().get("page"));
                assertEquals(PAGE_METADATA_REF, originalPage.get$ref());
                assertNull(originalPage.getExample());
            }

            @Test
            @DisplayName("Should leave other endpoints untouched")
            void shouldLeaveOtherEndpointsUntouched() {
                OpenAPI openApi = pageOpenApi(CLIENTS_PATH);
                Schema<?> before = responseSchemaOf(openApi, CLIENTS_PATH);

                apply(openApi);

                Schema<?> after = responseSchemaOf(openApi, CLIENTS_PATH);

                assertSame(before, after);
                assertEquals(PAGE_RESPONSE_REF, after.get$ref());
            }

            @Test
            @DisplayName("Should update an inline response schema in place")
            void shouldUpdateAnInlineSchemaInPlace() {
                ObjectSchema originalPage = pageMetadataSchema();
                ObjectSchema inline = new ObjectSchema();
                inline.addProperty("page", originalPage);
                OpenAPI openApi = getOpenApi(USERS_PATH, inline);

                apply(openApi);

                Schema<?> after = responseSchemaOf(openApi, USERS_PATH);
                Schema<?> page = after.getProperties().get("page");

                assertSame(inline, after);
                assertNotSame(originalPage, page);
                assertEquals(
                        Map.of("size", 20, "number", 0, "totalElements", 10, "totalPages", 1),
                        page.getExample()
                );
            }

            @Test
            @DisplayName("Should ignore a path that has no GET operation")
            void shouldIgnoreAPathWithoutGet() {
                OpenAPI openApi = openApiWith(USERS_PATH, new PathItem().post(operation()));

                assertDoesNotThrow(() -> apply(openApi));
            }

            @Test
            @DisplayName("Should ignore a GET operation without a 200 response")
            void shouldIgnoreAGetWithoutA200Response() {
                Operation get = operationWithResponse(
                        "404",
                        new ApiResponse().description("Not found.")
                );

                assertDoesNotThrow(() ->
                        apply(openApiWith(USERS_PATH, new PathItem().get(get))));

                assertNull(get.getResponses().get("200"));
            }

            @Test
            @DisplayName("Should ignore a 200 response without content")
            void shouldIgnoreA200ResponseWithoutContent() {
                Operation get = operationWithResponse(
                        "200",
                        new ApiResponse().description("OK.")
                );

                assertDoesNotThrow(() ->
                        apply(openApiWith(USERS_PATH, new PathItem().get(get))));

                assertNull(responseOf(get, "200").getContent());
            }

            @Test
            @DisplayName("Should ignore a 200 response with empty content")
            void shouldIgnoreA200ResponseWithEmptyContent() {
                Operation get = operationWithResponse(
                        "200",
                        new ApiResponse().description("OK.").content(new Content())
                );

                assertDoesNotThrow(() ->
                        apply(openApiWith(USERS_PATH, new PathItem().get(get))));

                assertTrue(responseOf(get, "200").getContent().isEmpty());
            }

            @ParameterizedTest(name = "{0}")
            @ValueSource(strings = {
                    "#/components/schemas/Missing",
                    "#/definitions/PageResponse"
            })
            @DisplayName("Should keep a response schema reference that cannot be resolved")
            void shouldKeepAnUnresolvableReference(String reference) {
                Schema<?> original = new Schema<>().$ref(reference);
                OpenAPI openApi = getOpenApi(USERS_PATH, original);
                openApi.setComponents(
                        new Components().addSchemas("PageResponse", new ObjectSchema())
                );

                apply(openApi);

                assertSame(original, responseSchemaOf(openApi, USERS_PATH));
                assertEquals(reference, original.get$ref());
            }

            @Test
            @DisplayName("Should ignore a response schema without properties")
            void shouldIgnoreAResponseSchemaWithoutProperties() {
                ObjectSchema inline = new ObjectSchema();
                OpenAPI openApi = getOpenApi(USERS_PATH, inline);

                assertDoesNotThrow(() -> apply(openApi));

                assertSame(inline, responseSchemaOf(openApi, USERS_PATH));
                assertNull(inline.getProperties());
            }

            @Test
            @DisplayName("Should not add a page property when the response has none")
            void shouldNotAddAPageProperty() {
                ObjectSchema inline = new ObjectSchema();
                inline.addProperty("content", new ArraySchema().items(new StringSchema()));
                OpenAPI openApi = getOpenApi(USERS_PATH, inline);

                apply(openApi);

                assertEquals(Set.of("content"), inline.getProperties().keySet());
            }

            @Test
            @DisplayName("Should keep a page property whose reference cannot be resolved")
            void shouldKeepAnUnresolvablePageProperty() {
                Schema<?> unresolved = new Schema<>().$ref("#/components/schemas/Missing");
                ObjectSchema inline = new ObjectSchema();
                inline.addProperty("page", unresolved);
                OpenAPI openApi = getOpenApi(USERS_PATH, inline);

                apply(openApi);

                assertSame(unresolved, inline.getProperties().get("page"));
                assertNull(unresolved.getExample());
            }

            @Test
            @DisplayName("Should keep a page property without properties")
            void shouldKeepAPagePropertyWithoutProperties() {
                ObjectSchema emptyPage = new ObjectSchema();
                ObjectSchema inline = new ObjectSchema();
                inline.addProperty("page", emptyPage);
                OpenAPI openApi = getOpenApi(USERS_PATH, inline);

                apply(openApi);

                assertSame(emptyPage, inline.getProperties().get("page"));
                assertNull(emptyPage.getExample());
            }
        }
    }

    // ------------------------------------------------------------------
    // Providers
    // ------------------------------------------------------------------

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

    @SuppressWarnings("unused")
    static Stream<Arguments> pageMetadataProperties() {
        return Stream.of(
                Arguments.of(
                        "size",
                        "Number of elements per page. The default is endpoint-specific.",
                        10
                ),
                Arguments.of(
                        "number",
                        "Zero-based index of the current page.",
                        0
                ),
                Arguments.of(
                        "totalElements",
                        "Total number of elements matching the query.",
                        10
                ),
                Arguments.of(
                        "totalPages",
                        "Total number of available pages.",
                        1
                )
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void apply(OpenAPI openApi) {
        config.globalResponses().customise(openApi);
    }

    private static OpenAPI emptyOpenApi() {
        return new OpenAPI().paths(new Paths());
    }

    private static OpenAPI openApiWith(String path, PathItem pathItem) {
        return new OpenAPI().paths(new Paths().addPathItem(path, pathItem));
    }

    private static Operation operation() {
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        return operation;
    }

    private static Operation operationWithResponse(String status, ApiResponse response) {
        Operation operation = operation();
        operation.getResponses().addApiResponse(status, response);
        return operation;
    }

    private static ApiResponse jsonResponse(String description, Schema<?> schema) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON, new MediaType().schema(schema)));
    }

    private static ApiResponse createdResponse() {
        return new ApiResponse()
                .description("Created.")
                .content(new Content().addMediaType("*/*", new MediaType()));
    }

    private static ApiResponse responseOf(Operation operation, String status) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response, "Expected a " + status + " response");

        return response;
    }

    private static MediaType jsonOf(ApiResponse response) {
        assertNotNull(response.getContent(), "Expected response content");

        MediaType mediaType = response.getContent().get(JSON);

        assertNotNull(mediaType, "Expected an application/json media type");

        return mediaType;
    }

    private static Map<?, ?> exampleOf(MediaType mediaType) {
        return assertInstanceOf(Map.class, mediaType.getExample());
    }

    private static ObjectSchema pageMetadataSchema() {
        ObjectSchema pageMetadata = new ObjectSchema();
        pageMetadata.addProperty("size", new IntegerSchema());
        pageMetadata.addProperty("number", new IntegerSchema());
        pageMetadata.addProperty("totalElements", new IntegerSchema());
        pageMetadata.addProperty("totalPages", new IntegerSchema());
        return pageMetadata;
    }

    /**
     * Builds a document whose GET operation on {@code path} returns a
     * {@code PageResponse} component that references {@code PageMetadata},
     * which mirrors what springdoc generates for paginated endpoints.
     */
    private static OpenAPI pageOpenApi(String path) {
        ObjectSchema pageResponse = new ObjectSchema();
        pageResponse.setTitle("PageResponse");
        pageResponse.setDescription("Paginated response.");
        pageResponse.addProperty("content", new ArraySchema().items(new StringSchema()));
        pageResponse.addProperty("page", new Schema<>().$ref(PAGE_METADATA_REF));
        pageResponse.setRequired(new ArrayList<>(List.of("content", "page")));

        OpenAPI openApi = getOpenApi(path, new Schema<>().$ref(PAGE_RESPONSE_REF));
        openApi.setComponents(new Components()
                .addSchemas("PageMetadata", pageMetadataSchema())
                .addSchemas("PageResponse", pageResponse));

        return openApi;
    }

    private static OpenAPI getOpenApi(String path, Schema<?> responseSchema) {
        Operation get = operationWithResponse("200", jsonResponse("OK.", responseSchema));
        return openApiWith(path, new PathItem().get(get));
    }

    private static Schema<?> responseSchemaOf(OpenAPI openApi, String path) {
        return openApi.getPaths()
                .get(path)
                .getGet()
                .getResponses()
                .get("200")
                .getContent()
                .get(JSON)
                .getSchema();
    }
}