package com.lcsalvess.bankingsystem.validation.fullname;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a full name meets the application's validation requirements.
 *
 * <p>Can be applied to fields, methods, parameters, annotation types,
 * constructors, type uses, and record components.
 */
@Documented
@Constraint(validatedBy = FullNameValidator.class)
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.ANNOTATION_TYPE,
    ElementType.CONSTRUCTOR,
    ElementType.TYPE_USE,
    ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFullName {

  String message() default ValidationMessages.INVALID_FULL_NAME;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
