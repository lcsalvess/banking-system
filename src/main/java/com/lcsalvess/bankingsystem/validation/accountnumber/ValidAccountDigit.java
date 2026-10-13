package com.lcsalvess.bankingsystem.validation.accountnumber;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Define uma anotação de validação personalizada para o dígito da conta.
 */
@Documented
// Faz a anotação aparecer na documentação gerada pelo Javadoc.
@Constraint(validatedBy = ValidAccountDigitValidator.class)
// Informa ao Bean Validation que esta é uma constraint personalizada.
// Quando @ValidAccountDigit for encontrada, ValidAccountDigitValidator será executado.
@Target({ElementType.FIELD, ElementType.PARAMETER})
// Define onde @ValidAccountDigit pode ser utilizada.
// FIELD = atributos/campos.
// PARAMETER = parâmetros de métodos.
@Retention(RetentionPolicy.RUNTIME)
// Mantém a anotação disponível durante a execução da aplicação.
// Isso permite que o Bean Validation consiga encontrá-la em runtime.
public @interface ValidAccountDigit {

    String message() default ValidationMessages.INVALID_ACCOUNT_CHECK_DIGIT_LENGTH;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
