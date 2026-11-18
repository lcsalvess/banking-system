package com.lcsalvess.bankingsystem.integration.address;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import com.lcsalvess.bankingsystem.integration.address.exception.PostalCodeNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates address lookups across configured providers and records lookup metrics.
 *
 * <p>Falls back to subsequent providers when an earlier provider is unavailable
 * or cannot find the requested postal code.
 */
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
    this.providers = List.copyOf(providers);

    this.addressLookupTimer = Timer.builder("address.lookup")
        .description("Total address lookup duration")
        .register(meterRegistry);

    this.providerTimers = this.providers.stream()
        .collect(Collectors.toMap(
            Function.identity(),
            provider -> Timer.builder("address.provider.lookup")
                .description("Address lookup duration by provider")
                .tag("provider", provider.getClass().getSimpleName())
                .register(meterRegistry)
        ));
  }

  /**
   * Retrieves address information using the configured providers.
   *
   * <p>Returns the first successful response. If all providers fail or cannot
   * find the postal code, the corresponding exception is propagated.
   *
   * @param postalCode postal code used to search for address information
   * @return address information retrieved from a provider
   * @throws AddressProviderUnavailableException if no available provider can complete the lookup
   * @throws PostalCodeNotFoundException if no provider finds the requested postal code
   */
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
