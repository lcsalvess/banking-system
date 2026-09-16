package com.lucas.sistemabancario.service.account;

import com.lucas.sistemabancario.repository.AccountRepository;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {
    private final AccountRepository accountRepository;


    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public GeneratedAccountNumber generate() {
        Long nextNumber = accountRepository.getNextAccountNumber();

        String number = String.format("%05d", nextNumber);
        String digit = calculateCheckDigit(number);
        return new GeneratedAccountNumber(number, digit);
    }

    public boolean isValid(String number, String digit) {
        return calculateCheckDigit(number).equals(digit);
    }

    private String calculateCheckDigit(String number) {
        int sum = 0;
        int weight = 2;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            sum += digit * weight;
            weight++;
            if (weight > 9) {
                weight = 2;
            }
        }
        int remainder = sum % 11;
        int checkDigit = 11 - remainder;
        if (checkDigit == 10 || checkDigit == 11) {
            checkDigit = 0;
        }
        return String.valueOf(checkDigit);
    }
}
