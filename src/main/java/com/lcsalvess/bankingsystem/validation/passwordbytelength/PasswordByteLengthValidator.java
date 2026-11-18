package com.lcsalvess.bankingsystem.validation.passwordbytelength;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * Validates that passwords do not exceed the maximum permitted UTF-8 byte length.
 *
 * <p>Uses UTF-8 encoding to measure the password size rather than counting
 * Java characters.
 */
public class PasswordByteLengthValidator
    implements ConstraintValidator<PasswordByteLength, String> {

  private static final int MAX_BYTES = 72;

  /**
   * Checks whether the supplied password fits within the maximum byte length.
   *
   * <p>Null values are considered valid so that constraints such as
   * {@code @NotBlank} can handle required-value validation independently.
   *
   * @param value the password to validate
   * @param context the constraint validator context
   * @return {@code true} if the value is null or contains at most 72 UTF-8 bytes;
   *     {@code false} otherwise
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }
}
