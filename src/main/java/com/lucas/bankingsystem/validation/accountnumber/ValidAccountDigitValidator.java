package com.lucas.bankingsystem.validation.accountnumber;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidAccountDigitValidator implements ConstraintValidator<ValidAccountDigit, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.matches("\\d");
    }
}
