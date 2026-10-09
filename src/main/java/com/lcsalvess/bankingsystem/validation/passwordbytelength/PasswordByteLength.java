package com.lcsalvess.bankingsystem.validation.passwordbytelength;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordByteLengthValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordByteLength {

    String message() default "A senha excede o limite de tamanho permitido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
