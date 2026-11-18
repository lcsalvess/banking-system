package com.lcsalvess.bankingsystem.validation.fullname;

import com.lcsalvess.bankingsystem.util.normalization.FullNameNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates full names using {@link FullNameNormalizer}.
 *
 * <p>Delegates character validation and normalization to the full name normalizer,
 * allowing null and blank values to be handled separately by {@code @NotBlank}.
 */
public class FullNameValidator implements ConstraintValidator<ValidFullName, String> {

  /**
   * Validates the supplied full name.
   *
   * <p>Null and blank values are considered valid so that {@code @NotBlank}
   * can handle those cases independently. Invalid names produce a constraint
   * violation using the exception message returned by the normalizer.
   *
   * @param value   the full name to validate
   * @param context the constraint validator context
   * @return {@code true} if the value is valid or blank; {@code false} otherwise
   */
  @Override
  public boolean isValid(
      String value,
      ConstraintValidatorContext context
  ) {
    // Let @NotBlank handle null and blank values using the same predicate.
    if (value == null || value.trim().isEmpty()) {
      return true;
    }

    try {
      FullNameNormalizer.normalize(value);
      return true;

    } catch (IllegalArgumentException exception) {

      context.disableDefaultConstraintViolation();

      context.buildConstraintViolationWithTemplate(exception.getMessage())
          .addConstraintViolation();

      return false;
    }
  }
}
