package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.JsonContentTypeCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.CLIENTS_PATH;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.JSON;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.openApiWith;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.operationWithResponse;
import static com.lcsalvess.bankingsystem.unit.config.openapi.OpenApiTestSupport.responseOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link JsonContentTypeCustomizer} directly, without a Spring context,
 * on hand-built {@link OpenAPI} documents.
 */
@DisplayName("JsonContentTypeCustomizer")
class JsonContentTypeCustomizerTests {

    private final JsonContentTypeCustomizer customizer = new JsonContentTypeCustomizer();

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

    private void apply(OpenAPI openApi) {
        customizer.customise(openApi);
    }
}
