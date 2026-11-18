package com.lcsalvess.bankingsystem.validation.reservedusername;

import com.lcsalvess.bankingsystem.util.actor.SystemActor;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a username does not match the reserved system username.
 *
 * <p>Uses the same normalization rules applied before username persistence
 * to ensure consistent validation.
 */
public class NotReservedUsernameValidator
    implements ConstraintValidator<NotReservedUsername, String> {

  private static final String RESERVED_USERNAME =
      UsernameNormalizer.normalize(SystemActor.NAME);

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    // Applies the same normalization used for the username before it is persisted.
    return !RESERVED_USERNAME.equals(UsernameNormalizer.normalize(value));
  }
}
