package com.lcsalvess.bankingsystem.validation.accountnumber;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a custom validation annotation for the account check digit.
 */
@Documented
// Makes the annotation appear in the generated Javadoc documentation.
@Constraint(validatedBy = ValidAccountDigitValidator.class)
// Informs Bean Validation that this is a custom constraint.
// Whenever @ValidAccountDigit is encountered, ValidAccountDigitValidator will be executed.
@Target({ElementType.FIELD, ElementType.PARAMETER})
// Defines where @ValidAccountDigit can be used.
// FIELD = fields.
// PARAMETER = method parameters.
@Retention(RetentionPolicy.RUNTIME)
// Keeps the annotation available at runtime.
// This allows Bean Validation to discover it during application execution.
public @interface ValidAccountDigit {

  String message() default ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
