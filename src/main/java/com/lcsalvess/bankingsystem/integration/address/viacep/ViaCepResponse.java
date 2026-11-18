package com.lcsalvess.bankingsystem.integration.address.viacep;

/**
 * Represents the address data returned by the ViaCEP API.
 *
 * @param cep the postal code returned by the provider
 * @param logradouro the street name
 * @param bairro the neighborhood
 * @param localidade the city or locality
 * @param uf the Brazilian federative unit abbreviation
 * @param erro indicates whether the requested postal code was not found
 */
public record ViaCepResponse(
    String cep,
    String logradouro,
    String bairro,
    String localidade,
    String uf,
    Boolean erro
) {
}
