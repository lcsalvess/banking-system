package com.lcsalvess.bankingsystem.validation.cpf;

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
     * Valida apenas os dígitos verificadores: nulo e formato ficam a cargo de
     * @NotBlank e @Pattern, evitando duas violações no mesmo campo.
     *
     * @param cpf valor do CPF que será validado.
     * @param context contexto fornecido pelo Bean Validation.
     * @return true se o CPF for válido ou não tiver 11 dígitos numéricos; false se os dígitos verificadores forem inválidos.
     */
    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        if (cpf == null || !cpf.matches("^[0-9]{11}$")) {
            return true;
        }
        return cpfValidator.isValid(cpf);
    }
}