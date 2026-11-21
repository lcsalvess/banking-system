package com.lcsalvess.bankingsystem.config.openapi.ui;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NullMarked;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

/**
 * Customizes the ordering of Swagger UI operations.
 */
@NullMarked
@Component
@ConditionalOnProperty(
    name = {"springdoc.enabled", "springdoc.swagger-ui.enabled"},
    havingValue = "true",
    matchIfMissing = true
)
public class SwaggerUiOperationsSorter extends SwaggerIndexPageTransformer {

  private static final String INITIALIZER_SCRIPT = "swagger-initializer.js";

  private static final String PRESETS_MARKER = "presets: [";

  private static final String OPERATIONS_SORTER_CONFIGURATION =
      """
      operationsSorter: (first, second) => {
        const methodOrder = [
          "get", "post", "patch", "delete", "put", "options", "head", "trace"
        ];

        const priority = (method) => {
          const index = methodOrder.indexOf(method);
          return index === -1 ? methodOrder.length : index;
        };

        const difference =
            priority(first.get("method")) - priority(second.get("method"));

        return difference
            || first.get("path").localeCompare(second.get("path"));
      },
      presets: [
      """;

  public SwaggerUiOperationsSorter(
      SwaggerUiConfigProperties swaggerUiConfig,
      SwaggerUiOAuthProperties swaggerUiOauthProperties,
      SwaggerWelcomeCommon swaggerWelcomeCommon,
      ObjectMapperProvider objectMapperProvider
  ) {
    super(
        swaggerUiConfig,
        swaggerUiOauthProperties,
        swaggerWelcomeCommon,
        objectMapperProvider
    );
  }

  /**
   * Applies custom operation ordering to the Swagger UI initializer.
   *
   * @param request          the HTTP request
   * @param resource         the resource being transformed
   * @param transformerChain the resource transformer chain
   * @return the transformed resource
   * @throws IOException if the resource cannot be read or transformed
   */
  @Override
  public Resource transform(
      HttpServletRequest request,
      Resource resource,
      ResourceTransformerChain transformerChain
  ) throws IOException {
    Resource transformedResource = super.transform(request, resource, transformerChain);

    if (!resource.getURL().toString().contains(INITIALIZER_SCRIPT)) {
      return transformedResource;
    }

    String initializer;

    try (InputStream inputStream = transformedResource.getInputStream()) {
      initializer = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    if (!initializer.contains(PRESETS_MARKER)) {
      throw new IOException("Could not find Swagger UI presets configuration marker.");
    }

    String updatedInitializer = initializer.replace(
        PRESETS_MARKER,
        OPERATIONS_SORTER_CONFIGURATION
    );

    return new TransformedResource(
        transformedResource,
        updatedInitializer.getBytes(StandardCharsets.UTF_8)
    );
  }
}
