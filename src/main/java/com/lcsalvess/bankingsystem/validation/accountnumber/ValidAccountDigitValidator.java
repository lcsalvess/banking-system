package com.lcsalvess.bankingsystem.validation.accountnumber;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidAccountDigitValidator implements ConstraintValidator<ValidAccountDigit, String> {

    private static final Pattern ACCOUNT_DIGIT_PATTERN = Pattern.compile("\\d");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return ACCOUNT_DIGIT_PATTERN.matcher(value).matches();
    }
}
