package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.PageSchemaCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.AUDIT_EVENTS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.CLIENTS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.PAGE_METADATA_REF;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.PAGE_RESPONSE_REF;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.USERS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.emptyOpenApi;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.getOpenApi;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.openApiWith;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operation;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operationWithResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.pageMetadataSchema;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.pageOpenApi;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseSchemaOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link PageSchemaCustomizer} directly, without a Spring context,
 * on hand-built {@link OpenAPI} documents.
 */
@DisplayName("PageSchemaCustomizer")
class PageSchemaCustomizerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.openapi.PageSchemaCustomizerTests#";

    private final PageSchemaCustomizer customizer = new PageSchemaCustomizer();

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
        @DisplayName("Should ignore the document when Components has no schemas")
        void shouldIgnoreWhenComponentsHaveNoSchemas() {
            OpenAPI openApi = emptyOpenApi();
            openApi.setComponents(new Components());

            assertDoesNotThrow(() -> apply(openApi));

            assertNull(openApi.getComponents().getSchemas());
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

        @Test
        @DisplayName("Should ignore page examples when Paths are not registered")
        void shouldIgnoreWhenPathsAreNull() {
            OpenAPI openApi = new OpenAPI();

            assertDoesNotThrow(() -> apply(openApi));

            assertNull(openApi.getPaths());
        }

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

        @Test
        @DisplayName("Should ignore a 200 JSON response without a schema")
        void shouldIgnoreA200ResponseWithoutSchema() {
            Operation get = operationWithResponse(
                    "200",
                    new ApiResponse()
                            .description("OK.")
                            .content(new Content()
                                    .addMediaType("application/json", new MediaType()))
            );

            OpenAPI openApi = openApiWith(USERS_PATH, new PathItem().get(get));

            assertDoesNotThrow(() -> apply(openApi));

            assertNull(
                    responseOf(get, "200")
                            .getContent()
                            .get("application/json")
                            .getSchema()
            );
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
        @DisplayName("Should keep a response reference when Components has no schemas")
        void shouldKeepReferenceWhenComponentsHaveNoSchemas() {
            Schema<?> original = new Schema<>().$ref(PAGE_RESPONSE_REF);
            OpenAPI openApi = getOpenApi(USERS_PATH, original);
            openApi.setComponents(new Components());

            apply(openApi);

            assertSame(original, responseSchemaOf(openApi, USERS_PATH));
            assertEquals(PAGE_RESPONSE_REF, original.get$ref());
            assertNull(openApi.getComponents().getSchemas());
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

    private void apply(OpenAPI openApi) {
        customizer.customise(openApi);
    }
}
