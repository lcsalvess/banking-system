package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI documentation for the Banking System API.
 *
 * <p>Defines API metadata, external documentation references, and
 * JWT bearer authentication requirements for protected endpoints.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Creates the OpenAPI specification for the Banking System API.
   *
   * <p>Configures the API title, version, description, source code reference,
   * and HTTP bearer authentication using JWT.
   *
   * @param applicationVersion the application version displayed in the API documentation
   * @return the configured OpenAPI specification
   */
  @Bean(name = "bankingSystemOpenAPI")
  public OpenAPI bankingSystemOpenApi(
      @Value("${info.app.version}") String applicationVersion
  ) {
    return new OpenAPI()
        .info(new Info()
            .title("Banking System API")
            .version(applicationVersion)
            .description(
                """
                RESTful banking API built with Java 21, Spring Boot, and PostgreSQL.

                Features include user and client management, bank account operations,
                deposits, withdrawals, transfers, transaction history, automated daily
                yield processing, and JWT-based authentication with role-based access
                control.

                To access protected endpoints, authenticate through the login endpoint
                and click Authorize to provide the JWT returned in the response.
                """
            )
        )
        .externalDocs(
            new ExternalDocumentation()
                .description("Source code on GitHub")
                .url("https://github.com/lcsalvess/banking-system")
        )
        .addSecurityItem(
            new SecurityRequirement()
                .addList("bearerAuth")
        )
        .components(
            new Components()
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
