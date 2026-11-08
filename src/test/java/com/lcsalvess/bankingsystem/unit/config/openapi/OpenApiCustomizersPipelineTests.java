package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.ErrorResponsesCustomizer;
import com.lcsalvess.bankingsystem.config.openapi.JsonContentTypeCustomizer;
import com.lcsalvess.bankingsystem.config.openapi.PageSchemaCustomizer;
import com.lcsalvess.bankingsystem.config.openapi.TransactionExamplesCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.DEPOSIT_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.ERROR_SCHEMA_REF;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.JSON;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.USERS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.createdResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.exampleOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.jsonOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operationWithResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.pageOpenApi;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseOf;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseSchemaOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Applies the OpenAPI customizers in the order springdoc runs them
 * ({@code AnnotationAwareOrderComparator}), which guards the ordering
 * contract between them: the customizers that read {@code application/json}
 * content depend on {@link JsonContentTypeCustomizer} having run first.
 */
@DisplayName("OpenAPI customizers pipeline")
class OpenApiCustomizersPipelineTests {

    private static List<OpenApiCustomizer> orderedCustomizers() {
        // Deliberately registered in reverse order: the sort must fix it.
        List<OpenApiCustomizer> customizers = new ArrayList<>(List.of(
                new PageSchemaCustomizer(),
                new TransactionExamplesCustomizer(),
                new JsonContentTypeCustomizer(),
                new ErrorResponsesCustomizer()
        ));

        AnnotationAwareOrderComparator.sort(customizers);

        return customizers;
    }

    @Test
    @DisplayName("Should run the JSON normalization before the customizers that read JSON content")
    void shouldRunJsonNormalizationFirst() {
        List<Class<?>> order = orderedCustomizers().stream()
                .<Class<?>>map(Object::getClass)
                .toList();

        assertEquals(
                List.of(
                        ErrorResponsesCustomizer.class,
                        JsonContentTypeCustomizer.class,
                        TransactionExamplesCustomizer.class,
                        PageSchemaCustomizer.class
                ),
                order
        );
    }

    @Test
    @DisplayName("Should document a generated document the same way as the single customizer did")
    void shouldCustomizeAGeneratedDocument() {
        Operation deposit = operationWithResponse("201", createdResponse());
        OpenAPI openApi = pageOpenApi(USERS_PATH);
        openApi.getPaths().addPathItem(DEPOSIT_PATH, new PathItem().post(deposit));

        orderedCustomizers().forEach(customizer -> customizer.customise(openApi));

        // 201 generated as */*: normalized to JSON, then given its example.
        Map<?, ?> example = exampleOf(jsonOf(responseOf(deposit, "201")));

        assertEquals("DEPOSIT", example.get("type"));

        // Error responses were added to the operation.
        assertEquals(
                ERROR_SCHEMA_REF,
                jsonOf(responseOf(deposit, "401")).getSchema().get$ref()
        );

        // The paginated response got its own schema with the page example.
        Schema<?> usersSchema = responseSchemaOf(openApi, USERS_PATH);
        Schema<?> page = usersSchema.getProperties().get("page");

        assertNull(usersSchema.get$ref());
        assertNotNull(page);
        assertEquals(
                Map.of("size", 20, "number", 0, "totalElements", 10, "totalPages", 1),
                assertInstanceOf(Map.class, page.getExample())
        );
        assertEquals(JSON, openApi.getPaths().get(USERS_PATH).getGet()
                .getResponses().get("200").getContent().keySet().iterator().next());
    }
}
