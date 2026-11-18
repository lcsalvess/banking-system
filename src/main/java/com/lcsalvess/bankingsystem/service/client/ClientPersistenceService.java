package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.event.client.ClientOperationEvent;
import com.lcsalvess.bankingsystem.event.client.ClientOperationType;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages client persistence operations and publishes events after changes.
 * <p>
 * Measures client creation and update execution time using Micrometer timers.
 */
@Service
public class ClientPersistenceService {

  private final ClientRepository clientRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Timer createTimer;
  private final Timer updateTimer;

  public ClientPersistenceService(
      ClientRepository clientRepository,
      ApplicationEventPublisher eventPublisher,
      MeterRegistry meterRegistry
  ) {
    this.clientRepository = clientRepository;
    this.eventPublisher = eventPublisher;
    this.createTimer = Timer.builder("client.persistence.create")
        .description(
            "Time spent persisting a client within the transaction "
                + "(excluding commit time)"
        )
        .register(meterRegistry);
    this.updateTimer = Timer.builder("client.persistence.update")
        .description("Time spent updating a client within the transaction (excluding commit time)")
        .register(meterRegistry);
  }

  /**
   * Persists a new client and publishes a creation event within the transaction.
   *
   * @param client the client to persist
   * @param username the username of the user responsible for the operation
   * @return the persisted client
   */
  @Transactional
  public Client create(
      Client client,
      String username
  ) {
    return createTimer.record(() -> {
      Client savedClient = clientRepository.save(client);

      publishClientOperationEvent(
          savedClient,
          ClientOperationType.CREATED,
          username
      );

      return savedClient;
    });
  }

  /**
   * Updates an existing client's personal, contact, and address information.
   * <p>
   * Only the supplied address data is applied when an address update is present.
   * Publishes an update event within the transaction.
   *
   * @param id the identifier of the client to update
   * @param data the data containing the client fields to update
   * @param username the username of the user responsible for the operation
   * @return the updated client
   * @throws ClientNotFoundException if no client exists with the supplied identifier
   */
  @Transactional
  public Client update(
      Long id,
      ClientUpdateData data,
      String username
  ) {
    return updateTimer.record(() -> {
      Client client = clientRepository.findById(id)
          .orElseThrow(ClientNotFoundException::new);

      client.update(
          data.name(),
          data.email(),
          data.phoneNumber()
      );

      AddressUpdateData address = data.address();

      if (address != null) {
        client.getAddress().updateFrom(
            address.addressData().streetName(),
            address.streetNumber(),
            address.complement(),
            address.addressData().neighborhood(),
            address.addressData().city(),
            address.addressData().state(),
            address.addressData().postalCode()
        );
      }

      Client updatedClient = clientRepository.save(client);

      publishClientOperationEvent(
          updatedClient,
          ClientOperationType.UPDATED,
          username
      );

      return updatedClient;
    });
  }

  private void publishClientOperationEvent(
      Client client,
      ClientOperationType operationType,
      String username
  ) {
    eventPublisher.publishEvent(
        new ClientOperationEvent(
            client.getId(),
            operationType,
            username
        )
    );
  }
}
