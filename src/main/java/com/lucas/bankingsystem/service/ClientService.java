package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.event.client.ClientOperationEvent;
import com.lucas.bankingsystem.event.client.ClientOperationType;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    private final AddressService addressService;

    private final ApplicationEventPublisher eventPublisher;

    public ClientService(ClientRepository clientRepository, AddressService addressService, ApplicationEventPublisher eventPublisher) {
        this.clientRepository = clientRepository;
        this.addressService = addressService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Client create(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException("CPF já cadastrado: " + dto.cpf());
        }

        Address address = addressService.createFromPostalCode(dto.address());

        Client client = new Client(dto.name(), dto.cpf(), dto.email(), dto.phoneNumber(), address);

        Client savedClient = clientRepository.save(client);

        eventPublisher.publishEvent(
                new ClientOperationEvent(savedClient.getId(), ClientOperationType.CREATED)
        );

        return savedClient;
    }

    @Transactional(readOnly = true)
    public List<ClientResponseDTO> findAll() {
        return clientRepository.findAll().stream().map(ClientResponseDTO::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public Client findEntityById(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException("Cliente não encontrado."));
    }

    @Transactional(readOnly = true)
    public ClientResponseDTO findById(Long id) {
        Client client = findEntityById(id);
        return ClientResponseDTO.fromEntity(client);
    }

    @Transactional
    public Client update(Long id, ClientUpdateRequestDTO dto) {
        Client existingClient = findEntityById(id);

        existingClient.update(dto);

        if (dto.address() != null) {
            addressService.updateFromPostalCode(existingClient.getAddress(), dto.address());
        }

        Client updatedClient = clientRepository.save(existingClient);

        eventPublisher.publishEvent(
                new ClientOperationEvent(updatedClient.getId(), ClientOperationType.UPDATED)
        );

        return updatedClient;
    }

}
