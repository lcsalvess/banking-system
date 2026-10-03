package com.lucas.bankingsystem.service.client;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.entity.enums.State;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.integration.address.dto.AddressLookupResponse;
import com.lucas.bankingsystem.repository.ClientRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPersistenceService {

    private final ClientRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ClientPersistenceService(
            ClientRepository clientRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Client create(
            ClientRequestDTO dto,
            Address address,
            String username
    ) {
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
    }

    @Transactional
    public Client update(
            Long id,
            ClientUpdateRequestDTO dto,
            AddressLookupResponse addressData,
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

        existingClient.update(dto);

        if (dto.address() != null) {
            Address address = existingClient.getAddress();

            address.setStreetName(addressData.streetName());
            address.setNeighborhood(addressData.neighborhood());
            address.setCity(addressData.city());
            address.setState(State.valueOf(addressData.state()));
            address.setPostalCode(addressData.postalCode().replace("-", ""));

            address.update(dto.address());
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