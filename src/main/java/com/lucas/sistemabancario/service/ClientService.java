package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ClientRequestDTO;
import com.lucas.sistemabancario.dto.request.ClientUpdateRequestDTO;
import com.lucas.sistemabancario.dto.response.ClientResponseDTO;
import com.lucas.sistemabancario.entity.Client;
import com.lucas.sistemabancario.exception.client.ClientCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.client.ClientNotFoundException;
import com.lucas.sistemabancario.repository.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public Client save(ClientRequestDTO dto) {
        if (clientRepository.existsByCpf(dto.cpf())) {
            throw new ClientCpfAlreadyExistsException("CPF já cadastrado: " + dto.cpf());
        }
        Client client = new Client(dto);
        return clientRepository.save(client);
    }

    public List<ClientResponseDTO> findAll() {
        return clientRepository.findAll()
                .stream()
                .map(ClientResponseDTO::fromEntity)
                .toList();
    }

    public Client findEntityById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("Cliente não encontrado."));
    }

    public ClientResponseDTO findById(Long id) {
        Client client = findEntityById(id);
        return ClientResponseDTO.fromEntity(client);
    }

    @Transactional
    public Client update(Long id, ClientUpdateRequestDTO dto) {
        Client existingClient = findEntityById(id);
        existingClient.update(dto);
        return clientRepository.save(existingClient);
    }

}
