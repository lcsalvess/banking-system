package com.lcsalvess.bankingsystem.integration.address.brasilapi;

public record BrasilApiResponse(
        String cep,
        String state,
        String city,
        String neighborhood,
        String street
) {
}
