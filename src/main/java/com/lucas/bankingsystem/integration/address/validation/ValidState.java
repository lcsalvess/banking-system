package com.lucas.bankingsystem.integration.address.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidState {

    String message() default "O Estado deve ser uma UF válida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
