package com.lcsalvess.bankingsystem.config.openapi;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.jspecify.annotations.NonNull;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Configuration
public class OpenApiConfig {

    private static final String PAGE_METADATA_SCHEMA = "PageMetadata";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String AUDIT_EVENTS_PATH = "/api/v1/audit-events";

    @Bean
    public OpenAPI bankingSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking System API")
                        .version("1.0.0")
                        .description("""
                                RESTful banking API built with Java 21, Spring Boot, and PostgreSQL.
                                
                                Features include user and client management, bank account operations, deposits, withdrawals, transfers, transaction history, automated daily yield processing, and JWT-based authentication with role-based access control.
                                
                                To access protected endpoints, authenticate through the login endpoint and click Authorize to provide the JWT returned in the response.
                                """))
                .externalDocs(new ExternalDocumentation()
                        .description("Source code on GitHub")
                        .url("https://github.com/lcsalvess/banking-system"))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                )
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }

    @Bean
    public OpenApiCustomizer globalResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            if (openApi.getComponents().getSchemas() == null) {
                openApi.getComponents().setSchemas(new LinkedHashMap<>());
            }

            if (!openApi.getComponents().getSchemas()
                    .containsKey(ApiErrorResponse.class.getSimpleName())) {

                SpringDocAnnotationsUtils.extractSchema(
                        openApi.getComponents(),
                        ApiErrorResponse.class,
                        null,
                        null,
                        openApi.getSpecVersion()
                );
            }

            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                        boolean isPublicLogin =
                                "/api/v1/auth/login".equals(path)
                                        && PathItem.HttpMethod.POST
                                        .equals(httpMethod);

                        if (!isPublicLogin) {
                            addResponseIfAbsent(
                                    operation,
                                    "401",
                                    "Unauthorized - Authentication is required or the JWT is invalid."
                            );

                            addResponseIfAbsent(
                                    operation,
                                    "403",
                                    "Forbidden - Insufficient permissions to access this resource."
                            );
                        }

                        addResponseIfAbsent(
                                operation,
                                "405",
                                "Method Not Allowed - The HTTP method is not supported."
                        );

                        addResponseIfAbsent(
                                operation,
                                "415",
                                "Unsupported Media Type - The request content type is not supported."
                        );

                        addResponseIfAbsent(
                                operation,
                                "500",
                                "Unexpected internal server error."
                        );

                        addResponseIfAbsent(
                                operation,
                                "503",
                                "Service Unavailable - A required infrastructure dependency "
                                        + "is temporarily unavailable."
                        );

                        operation.getResponses().forEach((status, response) -> {
                            if (status.matches("[45]\\d\\d")) {
                                response.setContent(errorContent(status));
                            }
                        });

                        operation.getResponses().forEach((status, response) -> {
                            if ("200".equals(status) || "201".equals(status)) {
                                Content content = response.getContent();

                                if (content != null && !content.isEmpty()) {
                                    MediaType mediaType = content.values().iterator().next();

                                    content.clear();
                                    content.addMediaType("application/json", mediaType);
                                }
                            }
                        });

                        if (PathItem.HttpMethod.POST.equals(httpMethod)) {
                            setTransactionExample(path, operation);
                        }
                    })
            );

            customizePageMetadata(openApi);
            customizePageExamples(openApi);
        };
    }

    private void customizePageMetadata(OpenAPI openApi) {
        if (openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Schema<?> pageMetadata = openApi.getComponents()
                .getSchemas()
                .get(PAGE_METADATA_SCHEMA);

        if (pageMetadata == null || pageMetadata.getProperties() == null) {
            return;
        }

        setProperty(
                pageMetadata,
                "size",
                "Number of elements per page. The default is endpoint-specific.",
                10
        );

        setProperty(
                pageMetadata,
                "number",
                "Zero-based index of the current page.",
                0
        );

        setProperty(
                pageMetadata,
                "totalElements",
                "Total number of elements matching the query.",
                10
        );

        setProperty(
                pageMetadata,
                "totalPages",
                "Total number of available pages.",
                1
        );
    }

    private void customizePageExamples(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        customizePageExample(openApi, USERS_PATH);
        customizePageExample(openApi, AUDIT_EVENTS_PATH);
    }

    private void customizePageExample(
            OpenAPI openApi,
            String path
    ) {
        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null || pathItem.getGet() == null) {
            return;
        }

        ApiResponse response = pathItem.getGet()
                .getResponses()
                .get("200");

        if (response == null
                || response.getContent() == null
                || response.getContent().get("application/json") == null) {
            return;
        }

        MediaType json = response.getContent().get("application/json");
        Schema<?> responseSchema = resolveSchema(json.getSchema(), openApi);

        if (responseSchema == null || responseSchema.getProperties() == null) {
            return;
        }

        if (json.getSchema() != null && json.getSchema().get$ref() != null) {
            ObjectSchema isolatedSchema = copyObjectSchema(responseSchema);
            json.setSchema(isolatedSchema);
            responseSchema = isolatedSchema;
        }

        Schema<?> pageProperty = responseSchema.getProperties().get("page");

        if (pageProperty == null) {
            return;
        }

        Schema<?> pageSchema = resolveSchema(pageProperty, openApi);

        if (pageSchema == null || pageSchema.getProperties() == null) {
            return;
        }

        responseSchema.addProperty(
                "page",
                createPageSchema(pageSchema)
        );
    }

    private static ObjectSchema createPageSchema(Schema<?> source) {
        ObjectSchema page = copyObjectSchema(source);

        LinkedHashMap<String, Object> example = new LinkedHashMap<>();
        example.put("size", 20);
        example.put("number", 0);
        example.put("totalElements", 10);
        example.put("totalPages", 1);

        page.setExample(example);

        return page;
    }

    private static ObjectSchema copyObjectSchema(Schema<?> source) {
        ObjectSchema copy = new ObjectSchema();

        copy.setDescription(source.getDescription());

        if (source.getTitle() != null) {
            copy.setTitle(source.getTitle());
        }

        if (source.getProperties() != null) {
            copy.setProperties(
                    new LinkedHashMap<>(source.getProperties())
            );
        }

        if (source.getRequired() != null) {
            copy.setRequired(
                    new ArrayList<>(source.getRequired())
            );
        }

        return copy;
    }

    private Schema<?> resolveSchema(
            Schema<?> schema,
            OpenAPI openApi
    ) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }

        String prefix = "#/components/schemas/";
        String reference = schema.get$ref();

        if (!reference.startsWith(prefix)
                || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return null;
        }

        String schemaName = reference.substring(prefix.length());

        return openApi.getComponents()
                .getSchemas()
                .get(schemaName);
    }

    private static void setProperty(
            Schema<?> parent,
            String propertyName,
            String description,
            Object example
    ) {
        Schema<?> property = parent.getProperties().get(propertyName);

        if (property == null) {
            return;
        }

        property.setDescription(description);
        property.setExample(example);
    }

    private void setTransactionExample(
            String path,
            Operation operation
    ) {
        String type = switch (path) {
            case "/api/v1/transactions/deposit" -> "DEPOSIT";
            case "/api/v1/transactions/withdraw" -> "WITHDRAWAL";
            case "/api/v1/transactions/transfer" -> "TRANSFER_SENT";
            default -> null;
        };

        if (type == null) {
            return;
        }

        ApiResponse response = operation.getResponses().get("201");

        if (response == null || response.getContent() == null) {
            return;
        }

        MediaType json = response.getContent().get("application/json");

        if (json == null) {
            return;
        }

        LinkedHashMap<String, Object> example = createExample(type);

        json.setExample(example);
    }

    private static @NonNull LinkedHashMap<String, Object> createExample(String type) {
        LinkedHashMap<String, Object> example = new LinkedHashMap<>();
        example.put(
                "transactionCode",
                "108cf2a1-bcee-460a-a5db-f9b231947411"
        );

        if ("TRANSFER_SENT".equals(type)) {
            example.put(
                    "transferCode",
                    "a12bc34d-5678-4abc-9def-1234567890ab"
            );
        }

        example.put("type", type);
        example.put("amount", 450.95);
        example.put("createdAt", "2026-10-15T14:30:00");
        return example;
    }

    private void addResponseIfAbsent(
            Operation operation,
            String status,
            String description
    ) {
        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            operation.getResponses().addApiResponse(
                    status,
                    new ApiResponse()
                            .description(description)
                            .content(errorContent(status))
            );
            return;
        }

        if (response.getContent() == null
                || response.getContent().isEmpty()) {
            response.setContent(errorContent(status));
        }
    }

    private Content errorContent(String status) {
        String message = switch (status) {
            case "400" -> "Request validation failed.";
            case "401" -> "Authentication is required or the JWT is invalid.";
            case "403" -> "Insufficient permissions to access this resource.";
            case "404" -> "The requested resource was not found.";
            case "405" -> "The HTTP method is not supported.";
            case "409" -> "The request conflicts with the current resource state.";
            case "415" -> "The request content type is not supported.";
            case "429" -> "Too many login attempts. Please wait before trying again.";
            case "503" -> "Service temporarily unavailable.";
            default -> "Unexpected internal server error.";
        };

        return new Content()
                .addMediaType(
                        "application/json",
                        new MediaType()
                                .schema(new Schema<>()
                                        .$ref("#/components/schemas/ApiErrorResponse"))
                                .example(java.util.Map.of(
                                        "status", Integer.parseInt(status),
                                        "message", message,
                                        "timestamp", "2026-10-15T18:30:00Z"
                                ))
                );
    }
}
