package com.lucas.bankingsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking System API")
                        .version("1.0.0")
                        .description("RESTful API for a banking system developed with Java and Spring Boot."))
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
            if (openApi.getComponents().getSchemas() == null) {
                openApi.getComponents().setSchemas(new LinkedHashMap<>());
            }

            openApi.getComponents().addSchemas(
                    "ProblemDetail",
                    problemDetailSchema()
            );

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        addResponseIfAbsent(
                                operation,
                                "401",
                                "Unauthorized - Authentication is required or the JWT is invalid.",
                                errorContent()
                        );

                        addResponseIfAbsent(
                                operation,
                                "403",
                                "Forbidden - Insufficient permissions to access this resource.",
                                errorContent()
                        );

                        addResponseIfAbsent(
                                operation,
                                "404",
                                "Not Found - The requested resource was not found.",
                                notFoundContent()
                        );

                        addResponseIfAbsent(
                                operation,
                                "405",
                                "Method Not Allowed - The HTTP method is not supported.",
                                problemDetailContent()
                        );

                        addResponseIfAbsent(
                                operation,
                                "415",
                                "Unsupported Media Type - The request content type is not supported.",
                                problemDetailContent()
                        );

                        addResponseIfAbsent(
                                operation,
                                "500",
                                "Unexpected internal server error.",
                                errorContent()
                        );
                        operation.getResponses().get("401").setContent(errorContent());
                        operation.getResponses().get("403").setContent(errorContent());
                        operation.getResponses().get("500").setContent(errorContent());
                    })
            );
        };
    }

    private void addResponseIfAbsent(
            io.swagger.v3.oas.models.Operation operation,
            String status,
            String description,
            Content content
    ) {
        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            operation.getResponses().addApiResponse(
                    status,
                    new ApiResponse()
                            .description(description)
                            .content(content)
            );
            return;
        }

        if (response.getContent() == null || response.getContent().isEmpty()) {
            response.setContent(content);
        }
    }

    private Schema<?> problemDetailSchema() {
        return new Schema<>()
                .type("object")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"));
    }

    private Content notFoundContent() {
        return new Content()
                .addMediaType(
                        "application/json",
                        new MediaType()
                                .schema(new Schema<>()
                                        .oneOf(List.of(
                                                new Schema<>().$ref(
                                                        "#/components/schemas/ErrorResponse"
                                                ),
                                                new Schema<>().$ref(
                                                        "#/components/schemas/ProblemDetail"
                                                )
                                        ))
                                )
                );
    }

    private Content problemDetailContent() {
        return new Content()
                .addMediaType(
                        "application/problem+json",
                        new MediaType()
                                .schema(new Schema<>()
                                        .$ref("#/components/schemas/ProblemDetail"))
                );
    }

    private Content errorContent() {
        return new Content()
                .addMediaType(
                        "application/json",
                        new MediaType()
                                .schema(new Schema<>()
                                        .$ref("#/components/schemas/ErrorResponse"))
                );
    }
}