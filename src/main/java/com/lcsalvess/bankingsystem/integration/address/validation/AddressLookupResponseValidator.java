package com.lcsalvess.bankingsystem.integration.address.validation;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Validates address lookup responses received from external providers.
 */
@Component
public class AddressLookupResponseValidator {

  private final Validator validator;

  public AddressLookupResponseValidator(Validator validator) {
    this.validator = validator;
  }

  /**
   * Validates an address lookup response and rejects responses containing constraint violations.
   *
   * @param response response received from the address provider
   * @return the validated address lookup response
   * @throws AddressProviderUnavailableException if the response contains validation errors
   */
  public AddressLookupResponse validate(AddressLookupResponse response) {
    Set<ConstraintViolation<AddressLookupResponse>> violations =
        validator.validate(response);

    if (!violations.isEmpty()) {
      String errors = violations.stream()
          .map(ConstraintViolation::getMessage)
          .collect(Collectors.joining("; "));

      throw new AddressProviderUnavailableException(
          ApiErrorMessages.ADDRESS_PROVIDER_INVALID_RESPONSE + errors
      );
    }

    return response;
  }
}
