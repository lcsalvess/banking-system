package com.lcsalvess.bankingsystem.validation.reservedusername;

import com.lcsalvess.bankingsystem.util.actor.SystemActor;
import com.lcsalvess.bankingsystem.util.normalization.UsernameNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotReservedUsernameValidator
        implements ConstraintValidator<NotReservedUsername, String> {

    private static final String RESERVED_USERNAME =
            UsernameNormalizer.normalize(SystemActor.NAME);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // Mesma normalização aplicada ao username antes de ser salvo.
        return !RESERVED_USERNAME.equals(UsernameNormalizer.normalize(value));
    }
}