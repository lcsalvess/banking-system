package com.lcsalvess.bankingsystem.serialization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializes monetary values as JSON numbers with exactly two fractional digits.
 */
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
