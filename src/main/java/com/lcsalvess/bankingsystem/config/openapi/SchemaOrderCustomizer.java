package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.TreeMap;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Sorts OpenAPI component schemas alphabetically.
 */
@Component
public class SchemaOrderCustomizer implements GlobalOpenApiCustomizer {

  /**
   * Applies alphabetical ordering to OpenAPI component schemas.
   *
   * @param openApi the OpenAPI specification
   */
  @Override
  public void customise(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      return;
    }

    var schemas = openApi.getComponents().getSchemas();

    if (schemas == null || schemas.isEmpty()) {
      return;
    }

    openApi.getComponents().setSchemas(new TreeMap<>(schemas));
  }
}
