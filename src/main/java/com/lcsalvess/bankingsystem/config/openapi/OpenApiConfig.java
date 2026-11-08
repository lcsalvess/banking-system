package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
}
