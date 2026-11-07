package com.lcsalvess.bankingsystem.validation.cpf;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a custom validation annotation for Brazilian CPF numbers.
 */
@Documented
// Includes the annotation in the generated Javadoc documentation.
@Constraint(validatedBy = CpfConstraintValidator.class)
// Registers this annotation as a custom Bean Validation constraint.
// CpfConstraintValidator is invoked whenever {@code @ValidCpf} is encountered.
@Target({ElementType.FIELD, ElementType.PARAMETER})
// Defines where {@code @ValidCpf} can be applied.
// FIELD = fields.
// PARAMETER = method and constructor parameters.
@Retention(RetentionPolicy.RUNTIME)
// Keeps the annotation available at runtime.
// This allows Bean Validation to discover it during application execution.
public @interface ValidCpf {

    /**
     * Message displayed when validation fails.
     */
    String message() default ValidationMessages.INVALID_CPF;

    /**
     * Allows multiple validation constraints to be grouped together.
     */
    Class<?>[] groups() default {};

    /**
     * Allows metadata to be associated with the validation constraint.
     */
    Class<? extends Payload>[] payload() default {};
}
