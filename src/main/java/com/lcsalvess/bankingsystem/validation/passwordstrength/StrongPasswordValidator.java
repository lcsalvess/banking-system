package com.lcsalvess.bankingsystem.validation.passwordstrength;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validates password strength based on character composition requirements.
 *
 * <p>Requires at least one uppercase letter, one lowercase letter,
 * one numeric digit, and one special character.
 */
public class StrongPasswordValidator
    implements ConstraintValidator<StrongPassword, String> {

  private static final Pattern UPPERCASE = Pattern.compile("\\p{Lu}");
  private static final Pattern LOWERCASE = Pattern.compile("\\p{Ll}");
  private static final Pattern DIGIT = Pattern.compile("[0-9]");
  private static final Pattern SPECIAL_CHARACTER =
      Pattern.compile("[\\p{P}\\p{S}]");

  /**
   * Checks whether the supplied password meets the strength requirements.
   *
   * <p>Null values are considered valid so that constraints such as
   * {@code @NotBlank} can handle required-value validation independently.
   *
   * @param value the password to validate
   * @param context the constraint validator context
   * @return {@code true} if the password contains all required character types;
   *     {@code false} otherwise
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return UPPERCASE.matcher(value).find()
        && LOWERCASE.matcher(value).find()
        && DIGIT.matcher(value).find()
        && SPECIAL_CHARACTER.matcher(value).find();
  }
}
