package com.lcsalvess.bankingsystem.integration.address.validation;

import com.lcsalvess.bankingsystem.entity.enums.State;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StateValidator implements ConstraintValidator<ValidState, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            State.valueOf(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}