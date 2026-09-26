package com.lucas.bankingsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.getResponses().addApiResponse(
                            "401",
                            new ApiResponse()
                                    .description("Unauthorized - Authentication is required or the JWT is invalid.")
                                    .content(errorContent())
                    );

                    operation.getResponses().addApiResponse(
                            "403",
                            new ApiResponse()
                                    .description("Forbidden - Insufficient permissions to access this resource.")
                                    .content(errorContent())
                    );

                    operation.getResponses().addApiResponse(
                            "500",
                            new ApiResponse()
                                    .description("Unexpected internal server error.")
                                    .content(errorContent())
                    );
                })
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
