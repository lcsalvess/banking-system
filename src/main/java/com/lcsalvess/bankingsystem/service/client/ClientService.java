package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.dto.request.ClientRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientSummaryResponseDTO;
import com.lcsalvess.bankingsystem.entity.Address;
import com.lcsalvess.bankingsystem.entity.Client;
import com.lcsalvess.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.service.address.AddressData;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.PageableTieBreaker;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ClientResponseDTO create(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException(
                    ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS
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
            if (DatabaseConstraint.CLIENT_CPF_UNIQUE.isViolatedBy(exception)) {
                throw new ClientCpfAlreadyExistsException(
                        ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,
                        exception
                );
            }

            if (DatabaseConstraint.CLIENT_EMAIL_UNIQUE.isViolatedBy(exception)) {
                throw new ClientEmailAlreadyExistsException(
                        ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS,
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<ClientSummaryResponseDTO> findAll(
            String name,
            Pageable pageable
    ) {
        Pageable sortedPageable = PageableTieBreaker.withIdTieBreaker(pageable);

        Page<Client> clients;

        if (name == null || name.isBlank()) {
            clients = clientRepository.findAll(sortedPageable);
        } else {
            clients = clientRepository.findByNameContainingIgnoreCase(
                    name.trim(),
                    sortedPageable
            );
        }

        return clients.map(ClientSummaryResponseDTO::fromEntity);
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