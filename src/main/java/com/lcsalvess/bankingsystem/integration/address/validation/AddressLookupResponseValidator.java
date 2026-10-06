package com.lcsalvess.bankingsystem.integration.address.validation;

import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import jakarta.validation.ConstraintViolation;
import org.springframework.stereotype.Component;

import jakarta.validation.Validator;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AddressLookupResponseValidator {

    private final Validator validator;

    public AddressLookupResponseValidator(Validator validator) {
        this.validator = validator;
    }

    public AddressLookupResponse validate(AddressLookupResponse response) {
        Set<ConstraintViolation<AddressLookupResponse>> violations = validator.validate(response);

        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));

            throw new AddressProviderUnavailableException(
                    "O provedor retornou um endereço inválido: " + errors
            );
        }

        return response;
    }
}
