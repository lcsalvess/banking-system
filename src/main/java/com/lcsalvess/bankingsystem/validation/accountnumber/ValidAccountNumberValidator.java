package com.lcsalvess.bankingsystem.validation.accountnumber;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validates bank account numbers.
 *
 * <p>Ensures that the supplied value contains between 5 and 20 numeric digits.
 */
public class ValidAccountNumberValidator
    implements ConstraintValidator<ValidAccountNumber, String> {

  private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\d{5,20}");

  /**
   * Checks whether the supplied account number has a valid format.
   *
   * <p>Null values are considered valid so that constraints such as
   * {@code @NotBlank} can handle required-value validation independently.
   *
   * @param value the account number to validate
   * @param context the constraint validator context
   * @return {@code true} if the value is null or contains between 5 and 20 digits;
   *     {@code false} otherwise
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return ACCOUNT_NUMBER_PATTERN.matcher(value).matches();
  }
}
