package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.address.AddressData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPersistenceService {

    private final ClientRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public ClientPersistenceService(
            ClientRepository clientRepository,
            ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry
    ) {
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Client create(
            ClientRequestDTO dto,
            Address address,
            String username
    ) {
        return Timer.builder("client.persistence.create")
                .description("Tempo total da persistência de um cliente")
                .register(meterRegistry)
                .record(() -> {
                    Client client = new Client(
                            dto.name(),
                            dto.cpf(),
                            dto.email(),
                            dto.phoneNumber(),
                            address
                    );

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
            ClientUpdateRequestDTO dto,
            AddressData addressData,
            String username
    ) {
        if (dto.address() != null && addressData == null) {
            throw new IllegalArgumentException(
                    "Os dados do endereço consultado são obrigatórios quando o endereço é informado."
            );
        }

        Client existingClient = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(
                        "Cliente não encontrado."
                ));

        existingClient.update(
                dto.name(),
                dto.email(),
                dto.phoneNumber()
        );

        if (dto.address() != null) {
            existingClient.getAddress().updateFrom(
                    addressData.streetName(),
                    dto.address().streetNumber(),
                    dto.address().complement(),
                    addressData.neighborhood(),
                    addressData.city(),
                    addressData.state(),
                    addressData.postalCode()
            );
        }

        Client updatedClient = clientRepository.save(existingClient);

        publishClientOperationEvent(
                updatedClient,
                ClientOperationType.UPDATED,
                username
        );

        return updatedClient;
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