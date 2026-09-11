package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ClienteRequestDTO;
import com.lucas.sistemabancario.dto.request.ClienteUpdateRequestDTO;
import com.lucas.sistemabancario.dto.request.EnderecoRequestDTO;
import com.lucas.sistemabancario.dto.response.ClienteResponseDTO;
import com.lucas.sistemabancario.entity.Cliente;
import com.lucas.sistemabancario.entity.enums.Estado;
import com.lucas.sistemabancario.entity.enums.TipoLogradouro;
import com.lucas.sistemabancario.exception.cliente.ClienteCpfAlreadyExistsException;
import com.lucas.sistemabancario.exception.cliente.ClienteNotFoundException;
import com.lucas.sistemabancario.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClienteServiceTest {
    @Mock
    private ClienteRepository clienteRepository;
    @InjectMocks
    private ClienteService clienteService;

    private ClienteRequestDTO criarDto() {
        EnderecoRequestDTO endereco = new EnderecoRequestDTO();
        endereco.setTipoLogradouro(TipoLogradouro.RUA);
        endereco.setLogradouro("Logradouro Teste");
        endereco.setNumero("123");
        endereco.setBairro("Bairro Teste");
        endereco.setCidade("Cidade Teste");
        endereco.setEstado(Estado.SP);
        endereco.setCep("12345678");

        ClienteRequestDTO dto = new ClienteRequestDTO();
        dto.setNome("Cliente Teste");
        dto.setCpf("12345678901");
        dto.setEmail("teste@email.com");
        dto.setTelefone("11999999999");
        dto.setEndereco(endereco);

        return dto;
    }

    @Nested
    @DisplayName("Testes de salvar cliente")
    class SalvarTests {
        @Test
        @DisplayName("Deve lançar exceção ao tentar salvar cliente com CPF já cadastrado.")
        void deveLancarExcecaoQuandoCpfJaExiste() {
            ClienteRequestDTO dto = criarDto();
            when(clienteRepository.existsByCpf(dto.getCpf())).thenReturn(true);
            assertThrows(ClienteCpfAlreadyExistsException.class, () -> clienteService.salvar(dto));
            verify(clienteRepository, never()).save(any(Cliente.class));
        }

        @Test
        @DisplayName("Deve salvar cliente com sucesso quando CPF estiver livre.")
        void deveSalvarClienteComSucesso() {
            ClienteRequestDTO dto = criarDto();
            when(clienteRepository.existsByCpf(dto.getCpf())).thenReturn(false);
            when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
                Cliente clienteSalvo = i.getArgument(0);
                ReflectionTestUtils.setField(clienteSalvo, "id", 1L);
                return clienteSalvo;
            });
            Cliente clienteResult = clienteService.salvar(dto);
            assertNotNull(clienteResult);
            assertEquals(1L, clienteResult.getId());
            assertEquals(dto.getCpf(), clienteResult.getCpf());
            assertEquals(dto.getNome(), clienteResult.getNome());
            verify(clienteRepository, times(1)).save(any(Cliente.class));
        }
    }

    @Nested
    @DisplayName("Teste de listagem de clientes")
    class ListarTests {

        @Test
        @DisplayName("Deve retornar lista de DTOs quando existirem clientes cadastrados.")
        void deveRetornarListaDeClientesQuandoExistiremRegistros() {
            Cliente cliente = new Cliente(criarDto());
            ReflectionTestUtils.setField(cliente, "id", 1L);
            when(clienteRepository.findAll()).thenReturn(List.of(cliente));
            List<ClienteResponseDTO> resultado = clienteService.listar();
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
            assertEquals(1L, resultado.getFirst().getId());
            assertEquals(cliente.getNome(), resultado.getFirst().getNome());
            assertEquals(cliente.getCpf(), resultado.getFirst().getCpf());
            verify(clienteRepository).findAll();
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando não existirem clientes cadastrados.")
        void deveRetornarListaVaziaQuandoNaoExistiremClientesCadastrados() {
            when(clienteRepository.findAll()).thenReturn(List.of());
            List<ClienteResponseDTO> resultado = clienteService.listar();
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(clienteRepository).findAll();
        }
    }

    @Nested
    @DisplayName("Testes de buscar cliente por ID")
    class BuscarClientePorIdTests {
        @Test
        @DisplayName("Deve retornar Cliente quando ID existir.")
        void deveRetornarClienteQuandoIdExistir() {
            //Arrange
            Cliente cliente = new Cliente(criarDto());
            ReflectionTestUtils.setField(cliente, "id", 1L);
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
            //Act
            Cliente resultado = clienteService.buscarClientePorId(1L);
            //Assert
            assertNotNull(resultado);
            assertEquals(1L, resultado.getId());
            assertEquals(cliente.getNome(), resultado.getNome());
            assertEquals(cliente.getCpf(), resultado.getCpf());
            //Verify
            verify(clienteRepository).findById(1L);
        }

        @Test
        @DisplayName("Deve lançar exceção quando Cliente não existir")
        void deveLancarExcecaoQuandoClienteNaoExistir() {
            Long idInexistente = 1L;
            when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
            assertThrows(ClienteNotFoundException.class, () -> clienteService.buscarClientePorId(idInexistente));
            verify(clienteRepository).findById(idInexistente);
        }
    }

    @Nested
    @DisplayName("Testes de buscar cliente por ID retornando DTO")
    class BuscarPorIdTests {
        @Test
        @DisplayName("Deve retornar ClienteResponseDTO quando ID existir")
        void deveRetornarClienteResponseDTOQuandoIdExistir() {
            Cliente cliente = new Cliente(criarDto());
            ReflectionTestUtils.setField(cliente, "id", 1L);
            when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
            ClienteResponseDTO resultado = clienteService.buscarPorId(1L);
            assertNotNull(resultado);
            assertEquals(cliente.getId(), resultado.getId());
            assertEquals(cliente.getNome(), resultado.getNome());
            assertEquals(cliente.getCpf(), resultado.getCpf());
            verify(clienteRepository).findById(1L);
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID não existir")
        void deveLancarExcecaoQuandoIdNaoExistir() {
            Long idInexistente = 1L;
            when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
            assertThrows(ClienteNotFoundException.class, () -> clienteService.buscarPorId(idInexistente));
            verify(clienteRepository).findById(idInexistente);
        }
    }

    @Nested
    @DisplayName("Testes de atualizar cliente")
    class AtualizarTests{
        private ClienteUpdateRequestDTO criarDtoAtualizado() {
            EnderecoRequestDTO endereco = new EnderecoRequestDTO();
            endereco.setTipoLogradouro(TipoLogradouro.AVENIDA);
            endereco.setLogradouro("Avenida Atualizada");
            endereco.setNumero("456");
            endereco.setBairro("Bairro Atualizado");
            endereco.setCidade("São Paulo");
            endereco.setEstado(Estado.SP);
            endereco.setCep("87654321");

            ClienteUpdateRequestDTO dto = new ClienteUpdateRequestDTO();
            dto.setNome("Cliente Atualizado");
            dto.setEmail("atualizado@email.com");
            dto.setTelefone("11888888888");
            dto.setEndereco(endereco);

            return dto;
        }

        @Test
        @DisplayName("Deve atualizar cliente com sucesso quando cliente existir")
        void deveAtualizarClienteQuandoExistir() {
            Long id = 1L;
            Cliente cliente = new Cliente(criarDto());
            ReflectionTestUtils.setField(cliente, "id", id);
            String cpfOriginal = cliente.getCpf();
            when(clienteRepository.findById(id)).thenReturn(Optional.of(cliente));
            ClienteUpdateRequestDTO dtoAtualizado = criarDtoAtualizado();
            when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
            Cliente resultado = clienteService.atualizar(id, dtoAtualizado);
            assertEquals(id, resultado.getId());
            assertEquals(dtoAtualizado.getNome(), resultado.getNome());
            assertEquals(cpfOriginal, resultado.getCpf());
            assertEquals(dtoAtualizado.getEmail(), resultado.getEmail());
            assertEquals(dtoAtualizado.getTelefone(), resultado.getTelefone());
            verify(clienteRepository).findById(id);
            verify(clienteRepository).save(resultado);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar atualizar um cliente inexistente")
        void deveLancarExcecaoAoAtualizarClienteInexistente() {
            Long idInexistente = 1L;
            ClienteUpdateRequestDTO dto = criarDtoAtualizado();
            when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
            assertThrows(ClienteNotFoundException.class, () -> clienteService.atualizar(idInexistente, dto));
            verify(clienteRepository).findById(idInexistente);
            verify(clienteRepository, never()).save(any(Cliente.class));
        }

    }
}
