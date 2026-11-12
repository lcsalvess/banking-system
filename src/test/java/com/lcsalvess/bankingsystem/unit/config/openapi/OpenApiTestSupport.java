package com.lcsalvess.bankingsystem.unit.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared builders and constants for the OpenAPI customizer tests.
 */
final class OpenApiTestSupport {

    static final String JSON = "application/json";

    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String CLIENTS_PATH = "/api/v1/clients";
    static final String USERS_PATH = "/api/v1/users";
    static final String AUDIT_EVENTS_PATH = "/api/v1/audit-events";
    static final String DEPOSIT_PATH = "/api/v1/transactions/deposits";
    static final String WITHDRAW_PATH = "/api/v1/transactions/withdrawals";
    static final String TRANSFER_PATH = "/api/v1/transactions/transfers";

    static final String ERROR_SCHEMA_NAME = "ApiErrorResponse";
    static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiErrorResponse";
    static final String PAGE_METADATA_REF = "#/components/schemas/PageMetadata";
    static final String PAGE_RESPONSE_REF = "#/components/schemas/PageResponse";

    private OpenApiTestSupport() {
    }

    static OpenAPI emptyOpenApi() {
        return new OpenAPI().paths(new Paths());
    }

    static OpenAPI openApiWith(String path, PathItem pathItem) {
        return new OpenAPI().paths(new Paths().addPathItem(path, pathItem));
    }

    static Operation operation() {
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        return operation;
    }

    static Operation operationWithResponse(String status, ApiResponse response) {
        Operation operation = operation();
        operation.getResponses().addApiResponse(status, response);
        return operation;
    }

    static ApiResponse jsonResponse(String description, Schema<?> schema) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON, new MediaType().schema(schema)));
    }

    static ApiResponse createdJsonResponse() {
        return new ApiResponse()
                .description("Created.")
                .content(new Content().addMediaType(JSON, new MediaType()));
    }

    static ApiResponse createdResponse() {
        return new ApiResponse()
                .description("Created.")
                .content(new Content().addMediaType("*/*", new MediaType()));
    }

    static ApiResponse responseOf(Operation operation, String status) {
        ApiResponse response = operation.getResponses().get(status);

        assertNotNull(response, "Expected a " + status + " response");

        return response;
    }

    static MediaType jsonOf(ApiResponse response) {
        assertNotNull(response.getContent(), "Expected response content");

        MediaType mediaType = response.getContent().get(JSON);

        assertNotNull(mediaType, "Expected an application/json media type");

        return mediaType;
    }

    static Map<?, ?> exampleOf(MediaType mediaType) {
        return assertInstanceOf(Map.class, mediaType.getExample());
    }

    static ObjectSchema pageMetadataSchema() {
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
    static OpenAPI pageOpenApi(String path) {
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

    static OpenAPI getOpenApi(String path, Schema<?> responseSchema) {
        Operation get = operationWithResponse("200", jsonResponse("OK.", responseSchema));
        return openApiWith(path, new PathItem().get(get));
    }

    static Schema<?> responseSchemaOf(OpenAPI openApi, String path) {
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
