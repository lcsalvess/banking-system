package com.lucas.bankingsystem.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementa a lógica de validação utilizada pela anotação @ValidCpf.
 */
public class CpfConstraintValidator
        implements ConstraintValidator<ValidCpf, String> {

    /*
     * Utiliza a classe responsável pelo algoritmo de validação do CPF.
     */
    private final CpfValidator cpfValidator = new CpfValidator();

    /**
     * Executa a validação quando o Bean Validation encontra a anotação @ValidCpf.
     *
     * @param cpf valor do CPF que será validado.
     * @param context contexto fornecido pelo Bean Validation.
     * @return true se o CPF for válido; false caso contrário.
     */
    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        return cpfValidator.isValid(cpf);
    }
}