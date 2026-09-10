package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.response.ClienteResponseDTO;
import com.lucas.sistemabancario.dto.request.ClienteRequestDTO;
import com.lucas.sistemabancario.entity.Cliente;
import com.lucas.sistemabancario.entity.Endereco;
import com.lucas.sistemabancario.exception.cliente.ClienteCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.cliente.ClienteNotFoundException;
import com.lucas.sistemabancario.repository.ClienteRepository;
import com.lucas.sistemabancario.repository.EnderecoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;

    public ClienteService(ClienteRepository clienteRepository, EnderecoRepository enderecoRepository) {
        this.clienteRepository = clienteRepository;
        this.enderecoRepository = enderecoRepository;
    }

    @Transactional
    public Cliente salvar(ClienteRequestDTO dto) {
        if (clienteRepository.existsByCpf(dto.getCpf())) {
            throw new ClienteCpfAlreadyExistsException("CPF já cadastrado: " + dto.getCpf());
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
                .orElseThrow(() -> new ClienteNotFoundException("Cliente não encontrado"));
    }

    public ClienteResponseDTO buscarPorId(Long id) {
        Cliente cliente = buscarClientePorId(id);
        return ClienteResponseDTO.fromEntity(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente cliente) {
        Cliente clienteExistente = buscarClientePorId(id);
        if (clienteRepository.existsByCpfAndIdNot(cliente.getCpf(), id)) {
            throw new ClienteCpfAlreadyExistsException("CPF já cadastrado: " + cliente.getCpf());
        }
        atualizarCliente(clienteExistente, cliente);
        atualizarEndereco(clienteExistente.getEndereco(), cliente.getEndereco());
        return clienteRepository.save(clienteExistente);
    }

    @Transactional
    public void deletarPorId(Long id) {
        Cliente cliente = buscarClientePorId(id);
        clienteRepository.delete(cliente);
    }

    private void atualizarEndereco(Endereco enderecoExistente, Endereco enderecoNovo) {
        enderecoExistente.setTipoLogradouro(enderecoNovo.getTipoLogradouro());
        enderecoExistente.setLogradouro(enderecoNovo.getLogradouro());
        enderecoExistente.setNumero(enderecoNovo.getNumero());
        enderecoExistente.setComplemento(enderecoNovo.getComplemento());
        enderecoExistente.setBairro(enderecoNovo.getBairro());
        enderecoExistente.setCidade(enderecoNovo.getCidade());
        enderecoExistente.setEstado(enderecoNovo.getEstado());
        enderecoExistente.setCep(enderecoNovo.getCep());
    }

    private void atualizarCliente(Cliente clienteExistente, Cliente clienteNovo) {
        clienteExistente.setNome(clienteNovo.getNome());
        clienteExistente.setCpf(clienteNovo.getCpf());
        clienteExistente.setEmail(clienteNovo.getEmail());
        clienteExistente.setTelefone(clienteNovo.getTelefone());
    }
}
