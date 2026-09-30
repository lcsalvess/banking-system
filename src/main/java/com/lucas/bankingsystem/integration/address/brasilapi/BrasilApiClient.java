package com.lucas.bankingsystem.integration.address.brasilapi;

import com.lucas.bankingsystem.integration.address.AddressProvider;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lucas.bankingsystem.integration.address.validation.AddressLookupResponseValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(2)
public class BrasilApiClient implements AddressProvider {

    private final RestClient restClient;
    private final AddressLookupResponseValidator responseValidator;

    public BrasilApiClient(
            RestClient.Builder restClientBuilder,
            JdkClientHttpRequestFactory requestFactory,
            @Value("${integration.address.brasilapi.base-url}") String baseUrl,
            AddressLookupResponseValidator responseValidator
    ) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.responseValidator = responseValidator;
    }

    @Override
    public AddressLookupResponse findByPostalCode(String postalCode) {
        try {
            BrasilApiResponse response = restClient.get()
                    .uri("/api/cep/v1/{postalCode}", postalCode)
                    .retrieve()
                    .body(BrasilApiResponse.class);

            if (response == null) {
                throw new AddressProviderUnavailableException("A Brasil API retornou uma resposta vazia.");
            }

            AddressLookupResponse address = new AddressLookupResponse(
                    response.street(),
                    response.neighborhood(),
                    response.city(),
                    response.state(),
                    response.cep() == null ? null : response.cep().replace("-", "")
            );

            return responseValidator.validate(address);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PostalCodeNotFoundException(
                    "CEP não encontrado: " + postalCode
            );
        } catch (RestClientException ex) {
            throw new AddressProviderUnavailableException(
                    "Não foi possível consultar a Brasil API.",
                    ex
            );
        }
    }
}