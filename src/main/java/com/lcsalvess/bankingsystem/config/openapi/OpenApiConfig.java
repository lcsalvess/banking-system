
package com.lcsalvess.bankingsystem.config.openapi;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.jspecify.annotations.NonNull;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking System API")
                        .version("1.0.0")
                        .description("""
                            RESTful banking API built with Java 21, Spring Boot, and PostgreSQL.

Features include user and client management, bank account operations, transaction processing, address lookup, and JWT-based authentication.

Authenticate using the login endpoint, then click Authorize to provide your JWT and access protected endpoints.
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
        };
    }

    private void setTransactionExample(
            String path,
            Operation operation
    ) {
        String type = switch (path) {
            case "/api/v1/transactions/deposit" -> "DEPOSIT";
            case "/api/v1/transactions/withdraw" -> "WITHDRAWAL";
            case "/api/v1/transactions/transfer" -> "TRANSFER_SENT";
            case "/api/v1/transactions/yield/{accountNumber}" -> "YIELD";
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