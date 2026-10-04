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
import com.lucas.bankingsystem.service.client.ClientUpdateData;
import com.lucas.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import com.lucas.bankingsystem.service.security.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientService {

    private static final String CPF_ALREADY_EXISTS_MESSAGE = "Já existe um cliente cadastrado com este CPF.";

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

    public ClientResponseDTO create(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException(
                    CPF_ALREADY_EXISTS_MESSAGE
            );
        }

        Address address = addressService.createFromPostalCode(dto.address());

        Client client = new Client(
                dto.name(),
                dto.cpf(),
                dto.email(),
                dto.phoneNumber(),
                address
        );

        try {
            Client savedClient = clientPersistenceService.create(
                    client,
                    getCurrentUsername()
            );

            return ClientResponseDTO.fromEntity(savedClient);
        } catch (DataIntegrityViolationException exception) {
            // Corrida no CPF: o existsByCpf passou,
            // mas outra requisição gravou antes.
            // Só traduz se o CPF realmente já existe.
            if (clientRepository.existsByCpf(dto.cpf())) {
                throw new ClientCpfAlreadyExistsException(
                        CPF_ALREADY_EXISTS_MESSAGE,
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ClientSummaryResponseDTO> findAll() {
        return clientRepository.findAll().stream().map(ClientSummaryResponseDTO::fromEntity).toList();
    }

    public Client findEntityById(Long id) {
        return clientRepository.findById(id).orElseThrow(ClientNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public ClientResponseDTO findById(Long id) {
        Client client = findEntityById(id);
        return ClientResponseDTO.fromEntity(client);
    }

    public ClientResponseDTO update(Long id, ClientUpdateRequestDTO dto) {
        if (!clientRepository.existsById(id)) {
            throw new ClientNotFoundException();
        }

        AddressUpdateData address = null;

        if (dto.address() != null) {
            AddressData addressData = addressService.findAddressByPostalCode(
                    dto.address().postalCode()
            );

            address = new AddressUpdateData(
                    addressData,
                    dto.address().streetNumber(),
                    dto.address().complement()
            );
        }

        Client updatedClient = clientPersistenceService.update(
                id,
                new ClientUpdateData(
                        dto.name(),
                        dto.email(),
                        dto.phoneNumber(),
                        address
                ),
                getCurrentUsername()
        );

        return ClientResponseDTO.fromEntity(updatedClient);
    }

    private String getCurrentUsername() {
        return currentUserService.getUsername();
    }
}