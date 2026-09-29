package com.lucas.bankingsystem.integration.address.viacep;

import com.lucas.bankingsystem.integration.address.AddressProvider;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ViaCepClient implements AddressProvider {

    private final RestClient restClient;

    public ViaCepClient(RestClient.Builder restClientBuilder, @Value("${integration.address.viacep.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public AddressLookupResponse findByPostalCode(String postalCode) {
        try {
            ViaCepResponse response = restClient.get()
                    .uri("/ws/{postalCode}/json/", postalCode)
                    .retrieve()
                    .body(ViaCepResponse.class);

            if (response == null || Boolean.TRUE.equals(response.erro())) {
                throw new PostalCodeNotFoundException(
                        "CEP não encontrado: " + postalCode
                );
            }

            return new AddressLookupResponse(
                    response.logradouro(),
                    response.bairro(),
                    response.localidade(),
                    response.uf(),
                    response.cep()
            );
        } catch (RestClientException ex) {
            throw new AddressProviderUnavailableException(
                    "Não foi possível consultar a ViaCEP.",
                    ex
            );
        }
    }
}
