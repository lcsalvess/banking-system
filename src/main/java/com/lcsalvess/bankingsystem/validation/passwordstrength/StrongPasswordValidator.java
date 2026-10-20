package com.lcsalvess.bankingsystem.validation.passwordstrength;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator
        implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern UPPERCASE = Pattern.compile("\\p{Lu}");
    private static final Pattern LOWERCASE = Pattern.compile("\\p{Ll}");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHARACTER =
            Pattern.compile("[\\p{P}\\p{S}]");

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