package com.lucas.bankingsystem.validation;

public class CpfValidator {
    public boolean isValid(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return false;
        }
        for (char character : cpf.toCharArray()) {
            if (!Character.isDigit(character)) {
                return false;
            }
        }
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        int[] digits = new int[11];
        for (int i = 0; i < 11; i++) {
            digits[i] = Character.getNumericValue(cpf.charAt(i));
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += digits[i] * (10 - i);
        }
        int remainder = sum % 11;

        int firstDigit;
        if (remainder < 2) {
            firstDigit = 0;
        } else {
            firstDigit = 11 - remainder;
        }
        if (digits[9] != firstDigit) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += digits[i] * (11 - i);
        }
        remainder = sum % 11;

        int secondDigit;
        if (remainder < 2) {
            secondDigit = 0;
        } else {
            secondDigit = 11 - remainder;
        }
        if (digits[10] != secondDigit) {
            return false;
        }

        return true;
    }
}