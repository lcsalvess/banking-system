package com.lucas.bankingsystem.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneySerializer extends ValueSerializer<BigDecimal> {
    @Override
    public void serialize(
            BigDecimal value,
            JsonGenerator generator,
            SerializationContext context
    ) {
        generator.writeNumber(value.setScale(2, RoundingMode.UNNECESSARY));
    }
}
