package com.lucas.bankingsystem.validation.accountnumber;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Define uma anotação de validação personalizada para o número da conta.
 */
@Documented
// Faz a anotação aparecer na documentação gerada pelo Javadoc.
@Constraint(validatedBy = ValidAccountNumberValidator.class)
// Informa ao Bean Validation que esta é uma constraint personalizada.
// Quando @ValidAccountNumber for encontrada, ValidAccountNumberValidator será executado.
@Target({ElementType.FIELD, ElementType.PARAMETER})
// Define onde @ValidAccountNumber pode ser utilizada.
// FIELD = atributos/campos.
// PARAMETER = parâmetros de métodos.
@Retention(RetentionPolicy.RUNTIME)
// Mantém a anotação disponível durante a execução da aplicação.
// Isso permite que o Bean Validation consiga encontrá-la em runtime.
public @interface ValidAccountNumber {

    String message() default "O número da conta deve conter 5 dígitos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
