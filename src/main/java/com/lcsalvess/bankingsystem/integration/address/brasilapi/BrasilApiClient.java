package com.lcsalvess.bankingsystem.integration.address.brasilapi;

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
                throw new AddressProviderUnavailableException(ApiErrorMessages.ADDRESS_PROVIDER_EMPTY_RESPONSE);
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
                    ApiErrorMessages.POSTAL_CODE_NOT_FOUND
            );
        } catch (RestClientException ex) {
            throw new AddressProviderUnavailableException(
                    ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE,
                    ex
            );
        }
    }
}