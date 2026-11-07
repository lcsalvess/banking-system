package com.lcsalvess.bankingsystem.service.account.generator;

import com.lcsalvess.bankingsystem.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public GeneratedAccountNumber generate() {
        Long nextNumber = accountRepository.getNextAccountNumber();

        String number = String.format(Locale.ROOT, "%05d", nextNumber);
        String digit = calculateCheckDigit(number);

        return new GeneratedAccountNumber(number, digit);
    }

    public boolean isValid(String number, String digit) {
        return number != null
                && number.length() >= 5
                && containsOnlyAsciiDigits(number)
                && digit != null
                && digit.length() == 1
                && containsOnlyAsciiDigits(digit)
                && calculateCheckDigit(number).equals(digit);
    }

    private boolean containsOnlyAsciiDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            if (character < '0' || character > '9') {
                return false;
            }
        }

        return true;
    }

    private String calculateCheckDigit(String number) {
        int sum = 0;
        int weight = 2;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';

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
