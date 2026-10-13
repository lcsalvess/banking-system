package com.lcsalvess.bankingsystem.integration.address.validation;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidState {

    String message() default ValidationMessages.INVALID_STATE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
