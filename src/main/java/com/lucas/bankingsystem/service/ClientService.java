package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.dto.response.ClientSummaryResponseDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import com.lucas.bankingsystem.service.address.AddressData;
import com.lucas.bankingsystem.service.client.ClientPersistenceService;
import com.lucas.bankingsystem.service.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    private final ClientPersistenceService clientPersistenceService;

    private final AddressService addressService;

    private final CurrentUserService currentUserService;

    public ClientService(ClientRepository clientRepository, ClientPersistenceService clientPersistenceService, AddressService addressService, CurrentUserService currentUserService) {
        this.clientRepository = clientRepository;
        this.clientPersistenceService = clientPersistenceService;
        this.addressService = addressService;
        this.currentUserService = currentUserService;
    }

    public Client create(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException(
                    "Já existe um cliente cadastrado com este CPF."
            );
        }

        Address address = addressService.createFromPostalCode(dto.address());

        String username = getCurrentUsername();

        return clientPersistenceService.create(dto, address, username);
    }

    @Transactional(readOnly = true)
    public List<ClientSummaryResponseDTO> findAll() {
        return clientRepository.findAll().stream().map(ClientSummaryResponseDTO::fromEntity).toList();
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

    public Client update(Long id, ClientUpdateRequestDTO dto) {

        String username = getCurrentUsername();

        AddressData addressData = dto.address() == null
                ? null
                : addressService.findAddressByPostalCode(
                dto.address().postalCode()
        );

        return clientPersistenceService.update(
                id,
                dto,
                addressData,
                username
        );
    }

    private String getCurrentUsername() {
        return currentUserService.getUsername();
    }
}
