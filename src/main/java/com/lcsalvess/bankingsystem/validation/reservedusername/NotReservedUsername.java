package com.lcsalvess.bankingsystem.validation.reservedusername;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NotReservedUsernameValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotReservedUsername {

    String message() default ValidationMessages.RESERVED_USERNAME;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}