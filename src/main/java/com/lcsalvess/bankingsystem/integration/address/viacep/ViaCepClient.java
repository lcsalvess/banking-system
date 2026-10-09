package com.lcsalvess.bankingsystem.integration.address.viacep;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.AddressProvider;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import com.lcsalvess.bankingsystem.integration.address.validation.AddressLookupResponseValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(1)
public class ViaCepClient implements AddressProvider {

    private final RestClient restClient;
    private final AddressLookupResponseValidator responseValidator;

    public ViaCepClient(
            RestClient.Builder restClientBuilder,
            JdkClientHttpRequestFactory requestFactory,
            @Value("${integration.address.viacep.base-url}") String baseUrl,
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
            ViaCepResponse response = restClient.get()
                    .uri("/ws/{postalCode}/json/", postalCode)
                    .retrieve()
                    .body(ViaCepResponse.class);

            if (response == null) {
                throw new AddressProviderUnavailableException(
                        ApiErrorMessages.ADDRESS_PROVIDER_EMPTY_RESPONSE
                );
            }

            if (Boolean.TRUE.equals(response.erro())) {
                throw new PostalCodeNotFoundException(
                        ApiErrorMessages.POSTAL_CODE_NOT_FOUND
                );
            }

            AddressLookupResponse address = new AddressLookupResponse(
                    response.logradouro(),
                    response.bairro(),
                    response.localidade(),
                    response.uf(),
                    response.cep() == null ? null : response.cep().replace("-", "")
            );

            return responseValidator.validate(address);

        } catch (RestClientException ex) {
            throw new AddressProviderUnavailableException(
                    ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE,
                    ex
            );
        }
    }
}
