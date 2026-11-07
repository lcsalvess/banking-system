package com.lcsalvess.bankingsystem.validation.cpf;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implements the validation logic for the {@link ValidCpf} annotation.
 */
public class CpfConstraintValidator
        implements ConstraintValidator<ValidCpf, String> {

    /*
     * Uses the class responsible for the CPF validation algorithm.
     */
    private final CpfValidator cpfValidator = new CpfValidator();

    /**
     * Validates a CPF when Bean Validation processes the {@link ValidCpf} annotation.
     *
     * <p>This validator checks only the check digits. Null and format validation
     * are delegated to {@code @NotBlank} and {@code @Pattern}, preventing duplicate
     * constraint violations for the same field.
     *
     * @param cpf the CPF value to validate.
     * @param context the context provided by Bean Validation.
     * @return {@code true} if the value is null, does not contain exactly 11 ASCII
     *         digits, or has valid check digits; {@code false} otherwise.
     */
    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        if (cpf == null || !cpf.matches("^[0-9]{11}$")) {
            return true;
        }
        return cpfValidator.isValid(cpf);
    }
}
