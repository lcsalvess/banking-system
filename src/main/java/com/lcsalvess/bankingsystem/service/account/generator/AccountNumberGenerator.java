package com.lcsalvess.bankingsystem.service.account.generator;

import com.lcsalvess.bankingsystem.repository.AccountRepository;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Generates account numbers and validates their check digits.
 */
@Component
public class AccountNumberGenerator {

  private final AccountRepository accountRepository;

  public AccountNumberGenerator(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /**
   * Generates a new account number using the next database sequence value
   * and calculates its corresponding check digit.
   *
   * @return the generated account number and check digit
   */
  public GeneratedAccountNumber generate() {
    Long nextNumber = accountRepository.getNextAccountNumber();

    String number = String.format(Locale.ROOT, "%05d", nextNumber);
    String digit = calculateCheckDigit(number);

    return new GeneratedAccountNumber(number, digit);
  }

  /**
   * Validates an account number and its corresponding check digit.
   *
   * <p>Both values must contain only ASCII digits. The account number must
   * contain at least five digits, and the check digit must contain exactly one.
   *
   * @param number account number to validate
   * @param digit check digit to validate
   * @return {@code true} if the account number and check digit are valid
   */
  public boolean isValid(String number, String digit) {
    return containsOnlyAsciiDigits(number)
        && number.length() >= 5
        && containsOnlyAsciiDigits(digit)
        && digit.length() == 1
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
