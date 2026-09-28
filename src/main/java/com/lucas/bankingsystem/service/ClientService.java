package com.lucas.bankingsystem.service;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.entity.Address;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.bankingsystem.exception.client.ClientNotFoundException;
import com.lucas.bankingsystem.repository.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    private final AddressService addressService;

    public ClientService(ClientRepository clientRepository, AddressService addressService) {
        this.clientRepository = clientRepository;
        this.addressService = addressService;
    }

    @Transactional
    public Client save(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException("CPF já cadastrado: " + dto.cpf());
        }

        Address address = addressService.createFromPostalCode(dto.address());

        Client client = new Client(dto.name(), dto.cpf(), dto.email(), dto.phoneNumber(), address);

        return clientRepository.save(client);
    }

    public List<ClientResponseDTO> findAll() {
        return clientRepository.findAll().stream().map(ClientResponseDTO::fromEntity).toList();
    }

    public Client findEntityById(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> new ClientNotFoundException("Cliente não encontrado."));
    }

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

        return clientRepository.save(existingClient);
    }

}
