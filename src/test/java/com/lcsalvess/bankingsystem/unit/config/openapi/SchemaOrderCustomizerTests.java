package com.lcsalvess.bankingsystem.unit.config.openapi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lcsalvess.bankingsystem.config.openapi.SchemaOrderCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class SchemaOrderCustomizerTests {

  private final SchemaOrderCustomizer customizer = new SchemaOrderCustomizer();

  @Test
  void shouldReturnWhenOpenApiHasNoComponents() {
    OpenAPI openApi = new OpenAPI();

    customizer.customise(openApi);

    assertNull(openApi.getComponents());
  }

  @Test
  void shouldReturnWhenComponentsHaveNoSchemas() {
    Components components = new Components();
    OpenAPI openApi = new OpenAPI().components(components);

    customizer.customise(openApi);

    assertNull(components.getSchemas());
  }

  @Test
  void shouldReturnWhenSchemasAreEmpty() {
    Components components = new Components().schemas(new LinkedHashMap<>());
    OpenAPI openApi = new OpenAPI().components(components);
    Map<?, ?> schemas = components.getSchemas();

    customizer.customise(openApi);

    assertSame(schemas, components.getSchemas());
    assertTrue(schemas.isEmpty());
  }

  @Test
  void shouldSortSchemasAlphabetically() {
    Schema<Object> transactionSchema = new Schema<>();
    Schema<Object> accountSchema = new Schema<>();
    Schema<Object> clientSchema = new Schema<>();

    Components components = new Components()
        .addSchemas("TransactionResponse", transactionSchema)
        .addSchemas("ClientResponse", clientSchema)
        .addSchemas("AccountResponse", accountSchema);

    OpenAPI openApi = new OpenAPI().components(components);
    Map<?, ?> originalSchemas = components.getSchemas();

    customizer.customise(openApi);

    Map<?, ?> sortedSchemas = components.getSchemas();

    assertNotSame(originalSchemas, sortedSchemas);
    assertInstanceOf(TreeMap.class, sortedSchemas);

    assertIterableEquals(
        List.of("AccountResponse", "ClientResponse", "TransactionResponse"),
        sortedSchemas.keySet()
    );

    assertSame(accountSchema, sortedSchemas.get("AccountResponse"));
    assertSame(clientSchema, sortedSchemas.get("ClientResponse"));
    assertSame(transactionSchema, sortedSchemas.get("TransactionResponse"));
  }
}
