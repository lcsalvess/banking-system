package com.lucas.bankingsystem.config;

import java.math.BigDecimal;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

@Configuration
public class JacksonConfig {

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