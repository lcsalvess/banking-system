package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link OpenApiConfig} directly, without a Spring context.
 * The response and schema customizers have their own test classes.
 */
@DisplayName("OpenApiConfig")
class OpenApiConfigTests {

    private final OpenApiConfig config = new OpenApiConfig();

    @Nested
    @DisplayName("bankingSystemOpenAPI")
    class BankingSystemOpenApiTests {

        private final OpenAPI openApi = config.bankingSystemOpenAPI();

        @Test
        @DisplayName("Should describe the API")
        void shouldDescribeTheApi() {
            Info info = openApi.getInfo();

            assertNotNull(info);
            assertEquals("Banking System API", info.getTitle());
            assertEquals("1.0.0", info.getVersion());
            assertTrue(info.getDescription().contains("RESTful banking API"));
            assertTrue(info.getDescription().contains("login endpoint"));
            assertTrue(info.getDescription().contains("Authorize"));
        }

        @Test
        @DisplayName("Should link to the source code")
        void shouldLinkToTheSourceCode() {
            assertNotNull(openApi.getExternalDocs());
            assertEquals("Source code on GitHub", openApi.getExternalDocs().getDescription());
            assertEquals(
                    "https://github.com/lcsalvess/banking-system",
                    openApi.getExternalDocs().getUrl()
            );
        }

        @Test
        @DisplayName("Should require the bearer authentication scheme globally")
        void shouldRequireBearerAuthGlobally() {
            List<SecurityRequirement> security = openApi.getSecurity();

            assertNotNull(security);
            assertEquals(1, security.size());
            assertEquals(Set.of("bearerAuth"), security.getFirst().keySet());
            assertEquals(List.of(), security.getFirst().get("bearerAuth"));
        }

        @Test
        @DisplayName("Should declare the bearer JWT security scheme")
        void shouldDeclareTheBearerJwtScheme() {
            Map<String, SecurityScheme> schemes = openApi.getComponents().getSecuritySchemes();

            assertEquals(Set.of("bearerAuth"), schemes.keySet());

            SecurityScheme scheme = schemes.get("bearerAuth");

            assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
            assertEquals("bearer", scheme.getScheme());
            assertEquals("JWT", scheme.getBearerFormat());
        }

        @Test
        @DisplayName("Should reference a declared security scheme in the global requirement")
        void shouldReferenceADeclaredSecurityScheme() {
            String requiredScheme = openApi.getSecurity().getFirst().keySet().iterator().next();

            assertTrue(openApi.getComponents().getSecuritySchemes().containsKey(requiredScheme));
        }
    }
}
