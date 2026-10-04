package com.lucas.bankingsystem.integration.address;

import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lucas.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AddressLookupService {

    private static final Logger log =
            LoggerFactory.getLogger(AddressLookupService.class);

    private final List<AddressProvider> providers;
    private final Timer addressLookupTimer;
    private final Map<AddressProvider, Timer> providerTimers;

    public AddressLookupService(
            List<AddressProvider> providers,
            MeterRegistry meterRegistry
    ) {
        this.providers = providers;

        this.addressLookupTimer = Timer.builder("address.lookup")
                .description("Tempo total da busca de endereço")
                .register(meterRegistry);

        this.providerTimers = providers.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        provider -> Timer.builder("address.provider.lookup")
                                .description("Tempo de consulta de cada provedor de endereço")
                                .tag("provider", provider.getClass().getSimpleName())
                                .register(meterRegistry)
                ));
    }

    public AddressLookupResponse findByPostalCode(String postalCode) {
        return addressLookupTimer.record(
                () -> findAddress(postalCode)
        );
    }

    private AddressLookupResponse findAddress(String postalCode) {
        Throwable lastFailureCause = null;

        for (AddressProvider provider : providers) {
            try {
                Timer timer = providerTimers.get(provider);

                return timer.record(
                        () -> provider.findByPostalCode(postalCode)
                );

            } catch (AddressProviderUnavailableException ex) {
                log.warn(
                        "Address provider unavailable: provider={}",
                        provider.getClass().getSimpleName()
                );
                lastFailureCause = ex;

            } catch (PostalCodeNotFoundException ex) {
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

        throw new PostalCodeNotFoundException(
                "CEP " + postalCode + " não encontrado em nenhum provedor."
        );
    }
}