package com.lcsalvess.bankingsystem.integration.address;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
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
                .description("Total address lookup duration")
                .register(meterRegistry);

        this.providerTimers = providers.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        provider -> Timer.builder("address.provider.lookup")
                                .description("Address lookup duration by provider")
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
                    ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE,
                    lastFailureCause
            );
        }

        throw new PostalCodeNotFoundException(
                ApiErrorMessages.POSTAL_CODE_NOT_FOUND
        );
    }
}