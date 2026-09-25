package com.lucas.bankingsystem.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Define uma anotação de validação personalizada para CPF.
 */
@Documented
// Faz a anotação aparecer na documentação gerada pelo Javadoc.
@Constraint(validatedBy = CpfConstraintValidator.class)
// Informa ao Bean Validation que esta é uma constraint personalizada.
// Quando @ValidCpf for encontrada, CpfConstraintValidator será executado.
@Target({ElementType.FIELD, ElementType.PARAMETER})
// Define onde @ValidCpf pode ser utilizada.
// FIELD = atributos/campos.
// PARAMETER = parâmetros de métodos.
@Retention(RetentionPolicy.RUNTIME)
// Mantém a anotação disponível durante a execução da aplicação.
// Isso permite que o Bean Validation consiga encontrá-la em runtime.
public @interface ValidCpf {

    /**
     * Mensagem exibida quando a validação falhar.
     */
    String message() default "CPF inválido.";

    /**
     * Permite agrupar diferentes regras de validação.
     */
    Class<?>[] groups() default {};

    /**
     * Permite associar metadados à validação.
     */
    Class<? extends Payload>[] payload() default {};
}