
package com.lcsalvess.bankingsystem.config;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                                response.setContent(errorContent());
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
                    })
            );
        };
    }

    private void addResponseIfAbsent(
            io.swagger.v3.oas.models.Operation operation,
            String status,
            String description
    ) {
        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            operation.getResponses().addApiResponse(
                    status,
                    new ApiResponse()
                            .description(description)
                            .content(errorContent())
            );
            return;
        }

        if (response.getContent() == null
                || response.getContent().isEmpty()) {
            response.setContent(errorContent());
        }
    }

    private Content errorContent() {
        return new Content()
                .addMediaType(
                        "application/json",
                        new MediaType()
                                .schema(new Schema<>()
                                        .$ref("#/components/schemas/ApiErrorResponse"))
                );
    }
}