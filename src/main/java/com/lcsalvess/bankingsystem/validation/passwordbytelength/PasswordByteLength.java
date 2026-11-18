package com.lcsalvess.bankingsystem.validation.passwordbytelength;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password does not exceed the permitted byte length.
 *
 * <p>Can be applied to fields, method parameters, and record components.
 */
@Documented
@Constraint(validatedBy = PasswordByteLengthValidator.class)
@Target({
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordByteLength {

  String message() default ValidationMessages.PASSWORD_EXCEEDS_MAX_BYTE_LENGTH;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
