package com.lcsalvess.bankingsystem.config.jackson;

import java.math.BigDecimal;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/**
 * Configures Jackson deserialization behavior for API request payloads.
 *
 * <p>Defines strict type coercion rules to prevent implicit conversions
 * that could result in unexpected input values.
 */
@Configuration
public class JacksonConfig {

  /**
   * Configures strict coercion rules for string and decimal values.
   *
   * <p>Rejects numeric and boolean values supplied as strings, and rejects
   * string values supplied for decimal fields.
   *
   * @return the Jackson builder customizer with the configured coercion rules
   */
  @Bean
  public JsonMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> {
      builder.withCoercionConfig(
          String.class,
          config -> config
              .setCoercion(
                  CoercionInputShape.Integer,
                  CoercionAction.Fail
              )
              .setCoercion(
                  CoercionInputShape.Float,
                  CoercionAction.Fail
              )
              .setCoercion(
                  CoercionInputShape.Boolean,
                  CoercionAction.Fail
              )
      );

      builder.withCoercionConfig(
          BigDecimal.class,
          config -> config
              .setCoercion(
                  CoercionInputShape.String,
                  CoercionAction.Fail
              )
      );
    };
  }
}
