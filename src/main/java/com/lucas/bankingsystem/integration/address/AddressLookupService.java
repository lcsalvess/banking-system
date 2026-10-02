package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressLookupService {

    private static final Logger log =
            LoggerFactory.getLogger(AddressLookupService.class);

    private final List<AddressProvider> providers;

    public AddressLookupService(List<AddressProvider> providers) {
        this.providers = providers;
    }

    public AddressLookupResponse findByPostalCode(String postalCode) {
        Throwable lastFailureCause = null;

        for (AddressProvider provider : providers) {
            try {
                return provider.findByPostalCode(postalCode);
            } catch (AddressProviderUnavailableException ex) {
                log.warn(
                        "Address provider unavailable: provider={}",
                        provider.getClass().getSimpleName()
                );
                lastFailureCause = ex;
            } catch (PostalCodeNotFoundException ex) {
                // Não achou neste provedor, tenta o próximo
                log.debug(
                        "Postal code not found in provider: provider={}",
                        provider.getClass().getSimpleName()
                );
            }
        }

        if (lastFailureCause != null) {
            throw new AddressProviderUnavailableException(
                    "Serviços de CEP indisponíveis no momento. Não foi possível validar o CEP: " + postalCode,
                    lastFailureCause
            );
        }

        throw new PostalCodeNotFoundException("CEP " + postalCode + " não encontrado em nenhum provedor.");
    }
}