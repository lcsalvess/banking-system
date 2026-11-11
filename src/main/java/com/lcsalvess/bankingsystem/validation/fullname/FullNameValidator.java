package com.lcsalvess.bankingsystem.validation.fullname;

import com.lcsalvess.bankingsystem.util.normalization.FullNameNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FullNameValidator implements ConstraintValidator<ValidFullName, String> {

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        // Let @NotBlank handle null and blank values using the same predicate.
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        try {
            FullNameNormalizer.normalize(value);
            return true;

        } catch (IllegalArgumentException exception) {

            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(exception.getMessage())
                    .addConstraintViolation();

            return false;
        }
    }
}
