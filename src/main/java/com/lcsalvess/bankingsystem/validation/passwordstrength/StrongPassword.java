
package com.lcsalvess.bankingsystem.validation.passwordstrength;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default ValidationMessages.INVALID_PASSWORD_STRENGTH;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}