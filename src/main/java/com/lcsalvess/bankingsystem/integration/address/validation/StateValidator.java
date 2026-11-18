package com.lcsalvess.bankingsystem.integration.address.validation;

import com.lcsalvess.bankingsystem.entity.address.State;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates Brazilian federative unit abbreviations.
 *
 * <p>Uses the {@link State} enum to verify whether a supplied value
 * represents a valid Brazilian state or the Federal District.
 */
public class StateValidator implements ConstraintValidator<ValidState, String> {

  /**
   * Checks whether the supplied value is a valid federative unit abbreviation.
   *
   * <p>Null and blank values are considered valid so that constraints such
   * as {@code @NotBlank} can handle them independently.
   *
   * @param value the federative unit abbreviation to validate
   * @param context the constraint validator context
   * @return {@code true} if the value is null, blank, or a valid state abbreviation;
   *     {@code false} otherwise
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true;
    }

    try {
      State.valueOf(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
