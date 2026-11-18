package com.lcsalvess.bankingsystem.integration.address.brasilapi;

/**
 * Represents the address information returned by BrasilAPI for a postal code lookup.
 *
 * @param cep the postal code
 * @param state the state abbreviation
 * @param city the city name
 * @param neighborhood the neighborhood name
 * @param street the street name
 */
public record BrasilApiResponse(
    String cep,
    String state,
    String city,
    String neighborhood,
    String street
) {
}
