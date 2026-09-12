package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ClienteRequestDTO;
import com.lucas.sistemabancario.dto.request.ClienteUpdateRequestDTO;
import com.lucas.sistemabancario.dto.response.ClienteResponseDTO;
import com.lucas.sistemabancario.entity.Cliente;
import com.lucas.sistemabancario.exception.cliente.ClientCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.cliente.ClientNotFoundException;
import com.lucas.sistemabancario.repository.ClienteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {
    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public Cliente salvar(ClienteRequestDTO dto) {
        if (clienteRepository.existsByCpf(dto.getCpf())) {
            throw new ClientCpfAlreadyExistsException("CPF já cadastrado: " + dto.getCpf());
        }
        Cliente cliente = new Cliente(dto);
        return clienteRepository.save(cliente);
    }

    public List<ClienteResponseDTO> listar() {
        return clienteRepository.findAll()
                .stream()
                .map(ClienteResponseDTO::fromEntity)
                .toList();
    }

    public Cliente buscarClientePorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("Cliente não encontrado"));
    }

    public ClienteResponseDTO buscarPorId(Long id) {
        Cliente cliente = buscarClientePorId(id);
        return ClienteResponseDTO.fromEntity(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, ClienteUpdateRequestDTO dto) {
        Cliente clienteExistente = buscarClientePorId(id);
        clienteExistente.atualizarInformacoes(dto);
        return clienteRepository.save(clienteExistente);
    }

    @Transactional
    public void deletarPorId(Long id) {
        Cliente cliente = buscarClientePorId(id);
        clienteRepository.delete(cliente);
    }
}
