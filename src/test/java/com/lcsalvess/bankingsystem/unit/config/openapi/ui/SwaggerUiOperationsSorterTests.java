package com.lcsalvess.bankingsystem.unit.config.openapi.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.lcsalvess.bankingsystem.config.openapi.ui.SwaggerUiOperationsSorter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

class SwaggerUiOperationsSorterTests {

  private static final String INITIALIZER_URL =
      "file:/META-INF/resources/webjars/swagger-ui/5.35.1/swagger-initializer.js";

  private static final String CSS_RESOURCE_URL =
      "file:/META-INF/resources/webjars/swagger-ui/5.35.1/swagger-ui.css";

  private static final String INITIALIZER_CONTENT =
      """
      window.onload = function() {
        const ui = SwaggerUIBundle({
          presets: [
            SwaggerUIBundle.presets.apis
          ]
        });
      };
      """;

  private final SwaggerUiConfigProperties swaggerUiConfig =
      new SwaggerUiConfigProperties();

  private final SwaggerUiOAuthProperties swaggerUiOauthProperties =
      new SwaggerUiOAuthProperties();

  private final SwaggerWelcomeCommon swaggerWelcomeCommon =
      mock(SwaggerWelcomeCommon.class);

  private final ObjectMapperProvider objectMapperProvider =
      new ObjectMapperProvider(new SpringDocConfigProperties());

  private final HttpServletRequest request = mock(HttpServletRequest.class);

  private final ResourceTransformerChain transformerChain =
      mock(ResourceTransformerChain.class);

  private SwaggerUiOperationsSorter customizer;

  @BeforeEach
  void setUp() {
    customizer = new SwaggerUiOperationsSorter(
        swaggerUiConfig,
        swaggerUiOauthProperties,
        swaggerWelcomeCommon,
        objectMapperProvider
    );
  }

  @Test
  void shouldReturnOriginalResourceWhenResourceIsNotInitializer() throws IOException {
    Resource resource = createResource(CSS_RESOURCE_URL, "swagger-ui styles");

    Resource result = customizer.transform(request, resource, transformerChain);

    assertSame(resource, result);
  }

  @Test
  void shouldInjectCustomOperationsSorterWhenInitializerContainsPresets()
      throws IOException {
    Resource resource = createResource(INITIALIZER_URL, INITIALIZER_CONTENT);

    Resource result = customizer.transform(request, resource, transformerChain);

    assertInstanceOf(TransformedResource.class, result);

    String initializer = readContent(result);

    assertTrue(initializer.contains("operationsSorter: (first, second) => {"));

    assertTrue(initializer.contains(
        "\"get\", \"post\", \"patch\", \"delete\", \"put\", \"options\", \"head\", \"trace\""
    ));

    assertTrue(initializer.contains(
        "first.get(\"path\").localeCompare(second.get(\"path\"))"
    ));

    assertTrue(initializer.contains("presets: ["));

    assertTrue(initializer.indexOf("\"get\"") < initializer.indexOf("\"post\""));

    assertTrue(initializer.indexOf("\"post\"") < initializer.indexOf("\"patch\""));

    assertTrue(initializer.indexOf("\"patch\"") < initializer.indexOf("\"delete\""));
  }

  @Test
  void shouldThrowIoExceptionWhenInitializerDoesNotContainPresets()
      throws IOException {
    Resource resource = createResource(
        INITIALIZER_URL,
        "window.onload = function() { const ui = SwaggerUIBundle({}); };"
    );

    IOException exception = assertThrows(
        IOException.class,
        () -> customizer.transform(request, resource, transformerChain)
    );

    assertTrue(
        exception.getMessage().contains(
            "Could not find Swagger UI presets configuration marker."
        )
    );
  }

  private static Resource createResource(String location, String content)
      throws IOException {
    URL resourceUrl = URI.create(location).toURL();

    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
      @Override
      @NullMarked
      public URL getURL() {
        return resourceUrl;
      }

      @Override
      public long lastModified() {
        return 0L;
      }
    };
  }

  private static String readContent(Resource resource) throws IOException {
    try (InputStream inputStream = resource.getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
