package com.lcsalvess.bankingsystem.validation.accountnumber;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validates account check digits.
 *
 * <p>Ensures that the supplied value contains exactly one numeric digit.
 */
public class ValidAccountDigitValidator
    implements ConstraintValidator<ValidAccountDigit, String> {

  private static final Pattern ACCOUNT_DIGIT_PATTERN = Pattern.compile("\\d");

  /**
   * Checks whether the supplied account check digit is valid.
   *
   * <p>Null values are considered valid so that constraints such as
   * {@code @NotBlank} can handle required-value validation independently.
   *
   * @param value the account check digit to validate
   * @param context the constraint validator context
   * @return {@code true} if the value is null or contains exactly one digit;
   *     {@code false} otherwise
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return ACCOUNT_DIGIT_PATTERN.matcher(value).matches();
  }
}
