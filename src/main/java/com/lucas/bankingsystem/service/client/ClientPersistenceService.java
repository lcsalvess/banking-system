package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .description("Tempo da persistência de um cliente dentro da transação (exclui o commit)")
                .register(meterRegistry);
        this.updateTimer = Timer.builder("client.persistence.update")
                .description("Tempo da atualização de um cliente dentro da transação (exclui o commit)")
                .register(meterRegistry);
    }

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