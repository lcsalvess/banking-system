
package com.lcsalvess.bankingsystem.validation.passwordstrength;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator
        implements ConstraintValidator<StrongPassword, String> {

    private static final String UPPERCASE = ".*[A-Z].*";
    private static final String LOWERCASE = ".*[a-z].*";
    private static final String DIGIT = ".*[0-9].*";
    private static final String SPECIAL_CHARACTER = ".*[^\\p{L}\\p{N}\\s].*";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return value.matches(UPPERCASE)
                && value.matches(LOWERCASE)
                && value.matches(DIGIT)
                && value.matches(SPECIAL_CHARACTER);
    }
}