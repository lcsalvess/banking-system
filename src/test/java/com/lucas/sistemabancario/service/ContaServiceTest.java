package com.lucas.sistemabancario.service;

import com.lucas.sistemabancario.dto.request.ClienteRequestDTO;
import com.lucas.sistemabancario.dto.request.ContaRequestDTO;
import com.lucas.sistemabancario.dto.request.EnderecoRequestDTO;
import com.lucas.sistemabancario.dto.response.ContaResponseDTO;
import com.lucas.sistemabancario.entity.Cliente;
import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaCorrente;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.entity.enums.Estado;
import com.lucas.sistemabancario.entity.enums.SituacaoConta;
import com.lucas.sistemabancario.entity.enums.TipoConta;
import com.lucas.sistemabancario.entity.enums.TipoLogradouro;
import com.lucas.sistemabancario.exception.conta.AccountAlreadyExistsException;
import com.lucas.sistemabancario.exception.conta.AccountHasBalanceException;
import com.lucas.sistemabancario.exception.conta.AccountIsNotActiveException;
import com.lucas.sistemabancario.exception.conta.AccountNotFoundException;
import com.lucas.sistemabancario.repository.ContaCorrenteRepository;
import com.lucas.sistemabancario.repository.ContaPoupancaRepository;
import com.lucas.sistemabancario.repository.ContaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContaServiceTest {
    @Mock
    private ContaRepository contaRepository;
    @Mock
    private ContaCorrenteRepository contaCorrenteRepository;
    @Mock
    private ContaPoupancaRepository contaPoupancaRepository;
    @Mock
    private ClienteService clienteService;
    @InjectMocks
    private ContaService contaService;

    private Cliente criarCliente() {
        EnderecoRequestDTO endereco = new EnderecoRequestDTO();
        endereco.setTipoLogradouro(TipoLogradouro.AVENIDA);
        endereco.setLogradouro("Teste");
        endereco.setNumero("123");
        endereco.setBairro("Centro");
        endereco.setCidade("Mogi das Cruzes");
        endereco.setEstado(Estado.SP);
        endereco.setCep("12345678");

        ClienteRequestDTO dto = new ClienteRequestDTO();
        dto.setNome("Cliente Teste");
        dto.setCpf("12345678901");
        dto.setEmail("teste@email.com");
        dto.setTelefone("11999999999");
        dto.setEndereco(endereco);

        Cliente cliente = new Cliente(dto);
        ReflectionTestUtils.setField(cliente, "id", 1L);
        return cliente;
    }

    private ContaCorrente criarContaCorrente() {
        Cliente cliente = criarCliente();
        ContaCorrente cc = new ContaCorrente(cliente, "000011");
        ReflectionTestUtils.setField(cc, "id", 1L);
        return cc;
    }

    private ContaPoupanca criarContaPoupanca() {
        Cliente cliente = criarCliente();
        ContaPoupanca cp = new ContaPoupanca(cliente, "000022", LocalDate.now());
        ReflectionTestUtils.setField(cp, "id", 2L);
        return cp;
    }

    private ContaRequestDTO criarDtoContaCorrente() {
        return new ContaRequestDTO(1L, TipoConta.CORRENTE);
    }

    private ContaRequestDTO criarDtoContaPoupanca() {
        return new ContaRequestDTO(2L, TipoConta.POUPANCA);
    }

    @Nested
    @DisplayName("Testes de listagem de contas")
    class ListarTests {
        @Test
        @DisplayName("Deve retornar lista de contas quando existirem registros.")
        void deveRetornarListaDeContasQuandoExistiremRegistros() {
            //Arrange
            ContaCorrente cc = criarContaCorrente();
            ContaPoupanca cp = criarContaPoupanca();
            when(contaRepository.findAll()).thenReturn(List.of(cc, cp));
            //Act
            List<ContaResponseDTO> resultado = contaService.listar();
            //Assert
            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(cc.getId(), resultado.getFirst().getId());
            assertEquals(cp.getId(), resultado.getLast().getId());
            //Verify
            verify(contaRepository).findAll();
        }

        @Test
        @DisplayName("Deve retornar lista vazia quando não existirem contas cadastradas.")
        void deveRetornarListaVaziaQuandoNaoExistiremRegistros() {
            //Arrange
            when(contaRepository.findAll()).thenReturn(List.of());
            //Act
            List<ContaResponseDTO> resultado = contaService.listar();
            //Assert
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            //Verify
            verify(contaRepository).findAll();
        }
    }

    @Nested
    @DisplayName("Testes de buscar conta por ID")
    class BuscarContaPorIdTests {
        @Test
        @DisplayName("Deve retornar Conta quando ID existir")
        void deveRetornarContaQuandoIdExistir() {
            //Arrange
            ContaCorrente cc = criarContaCorrente();
            when(contaRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
            //Act
            Conta resultado = contaService.buscarContaPorId(cc.getId());
            //Assert
            assertNotNull(resultado);
            assertEquals(cc.getId(), resultado.getId());
            assertEquals(cc.getNumeroConta(), resultado.getNumeroConta());
            assertEquals(cc.getTitular(), resultado.getTitular());
            assertEquals(cc.getTipoConta(), resultado.getTipoConta());
            assertEquals(cc.getSaldo(), resultado.getSaldo());
            //Verify
            verify(contaRepository).findById(cc.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID da conta não existir")
        void deveLancarExcecaoQuandoIdDaContaNaoExistir() {
            //Arrange
            Long idInexistente = 99L;
            when(contaRepository.findById(idInexistente)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> contaService.buscarContaPorId(idInexistente));
            //Verify
            verify(contaRepository).findById(idInexistente);
        }
    }

    @Nested
    @DisplayName("Testes de buscar conta por ID retornando DTO")
    class BuscarPorIdTests {
        @Test
        @DisplayName("Deve retornar ContaResponseDTO quando ID existir")
        void deveRetornarContaResponseDTOQuandoIdExistir() {
            // Arrange
            ContaPoupanca cp = criarContaPoupanca();
            when(contaRepository.findById(cp.getId())).thenReturn(Optional.of(cp));
            // Act
            ContaResponseDTO resultado = contaService.buscarPorId(cp.getId());
            // Assert
            assertNotNull(resultado);
            assertEquals(cp.getId(), resultado.getId());
            assertEquals(cp.getNumeroConta(), resultado.getNumeroConta());
            assertEquals(cp.getTipoConta(), resultado.getTipoConta());
            assertEquals(cp.getTitular().getNome(), resultado.getNomeTitular());
            // Verify
            verify(contaRepository).findById(cp.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando ID da conta não existir")
        void deveLancarExcecaoQuandoIdDaContaNaoExistir() {
            //Arrange
            Long idInexistente = 99L;
            when(contaRepository.findById(idInexistente)).thenReturn(Optional.empty());
            //Act + Assert
            assertThrows(AccountNotFoundException.class, () -> contaService.buscarPorId(idInexistente));
            //Verify
            verify(contaRepository).findById(idInexistente);
        }
    }

    @Nested
    @DisplayName("Testes de criação de contas")
    class CriarTests {
        @Test
        @DisplayName("Deve criar conta corrente com sucesso")
        void deveCriarContaCorrenteComSucesso() {
            // Arrange
            ContaRequestDTO dto = criarDtoContaCorrente();
            Cliente cliente = criarCliente();
            when(clienteService.buscarClientePorId(dto.getTitularId())).thenReturn(cliente);
            when(contaCorrenteRepository.existsByTitularId(dto.getTitularId())).thenReturn(false);
            when(contaRepository.gerarProximoNumeroConta()).thenReturn(1L);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta contaSalva = invocation.getArgument(0);
                ReflectionTestUtils.setField(contaSalva, "id", 1L);
                return contaSalva;
            });
            // Act
            ContaResponseDTO resultado = contaService.criar(dto);
            // Assert
            assertNotNull(resultado);
            assertEquals(1L, resultado.getId());
            assertEquals("000011", resultado.getNumeroConta());
            assertEquals(TipoConta.CORRENTE, resultado.getTipoConta());
            assertEquals(cliente.getNome(), resultado.getNomeTitular());
            //Verify
            verify(clienteService).buscarClientePorId(dto.getTitularId());
            verify(contaCorrenteRepository).existsByTitularId(dto.getTitularId());
            verify(contaRepository).gerarProximoNumeroConta();
            verify(contaRepository).save(any(Conta.class));
        }

        @Test
        @DisplayName("Deve criar conta poupança com sucesso")
        void deveCriarContaPoupancaComSucesso() {
            ContaRequestDTO dto = criarDtoContaPoupanca();
            Cliente cliente = criarCliente();
            when(clienteService.buscarClientePorId(dto.getTitularId())).thenReturn(cliente);
            when(contaPoupancaRepository.existsByTitularId(dto.getTitularId())).thenReturn(false);
            when(contaRepository.gerarProximoNumeroConta()).thenReturn(2L);
            when(contaRepository.save(any(Conta.class))).thenAnswer(invocation -> {
                Conta contaSalva = invocation.getArgument(0);
                ReflectionTestUtils.setField(contaSalva, "id", 2L);
                return contaSalva;
            });
            ContaResponseDTO resultado = contaService.criar(dto);
            assertNotNull(resultado);
            assertEquals(2L, resultado.getId());
            assertEquals("000022", resultado.getNumeroConta());
            assertEquals(TipoConta.POUPANCA, resultado.getTipoConta());
            assertEquals(cliente.getNome(), resultado.getNomeTitular());
            verify(clienteService).buscarClientePorId(dto.getTitularId());
            verify(contaPoupancaRepository).existsByTitularId(dto.getTitularId());
            verify(contaRepository).gerarProximoNumeroConta();
            verify(contaRepository).save(any(Conta.class));
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta corrente quando cliente já possuir uma")
        void deveLancarExcecaoAoTentarCriarContaCorrenteQuandoClienteJaPossuirUma() {
            ContaRequestDTO dto = criarDtoContaCorrente();
            Cliente cliente = criarCliente();
            when(clienteService.buscarClientePorId(dto.getTitularId())).thenReturn(cliente);
            when(contaCorrenteRepository.existsByTitularId(dto.getTitularId())).thenReturn(true);
            assertThrows(AccountAlreadyExistsException.class, () -> contaService.criar(dto));
            verify(clienteService).buscarClientePorId(dto.getTitularId());
            verify(contaCorrenteRepository).existsByTitularId(dto.getTitularId());
            verify(contaRepository, never()).gerarProximoNumeroConta();
            verify(contaRepository, never()).save(any(Conta.class));
        }

        @Test
        @DisplayName("Deve lançar exceção ao tentar criar conta poupança quando cliente já possuir uma")
        void deveLancarExcecaoAoTentarCriarContaPoupancaQuandoClienteJaPossuirUma() {
            ContaRequestDTO dto = criarDtoContaPoupanca();
            Cliente cliente = criarCliente();
            when(clienteService.buscarClientePorId(dto.getTitularId())).thenReturn(cliente);
            when(contaPoupancaRepository.existsByTitularId(dto.getTitularId())).thenReturn(true);
            assertThrows(AccountAlreadyExistsException.class, () -> contaService.criar(dto));
            verify(clienteService).buscarClientePorId(dto.getTitularId());
            verify(contaPoupancaRepository).existsByTitularId(dto.getTitularId());
            verify(contaRepository, never()).gerarProximoNumeroConta();
            verify(contaRepository, never()).save(any(Conta.class));
        }
    }

    @Nested
    @DisplayName("Testes de cancelamento de contas")
    class CancelarContaTests {
        @Test
        @DisplayName("Deve cancelar conta com sucesso quando ela está ativa e saldo zerado")
        void deveCancelarContaComSucessoQuandoEstaAtivaESaldoZerado() {
            ContaCorrente cc = criarContaCorrente();
            when(contaRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
            contaService.cancelarConta(cc.getId());
            assertEquals(SituacaoConta.CANCELADA, cc.getSituacaoConta());
            verify(contaRepository).findById(cc.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inexistente")
        void deveLancarExcecaoQuandoTentarCancelarContaInexistente() {
            Long idInexistente = 99L;
            when(contaRepository.findById(idInexistente)).thenReturn(Optional.empty());
            assertThrows(AccountNotFoundException.class, () -> contaService.cancelarConta(idInexistente));
            verify(contaRepository).findById(idInexistente);
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta inativa")
        void deveLancarExcecaoQuandoTentarCancelarContaInativa() {
            ContaPoupanca cp = criarContaPoupanca();
            ReflectionTestUtils.setField(cp, "situacaoConta", SituacaoConta.CANCELADA);
            when(contaRepository.findById(cp.getId())).thenReturn(Optional.of(cp));
            assertThrows(AccountIsNotActiveException.class, () -> contaService.cancelarConta(cp.getId()));
            verify(contaRepository).findById(cp.getId());
        }

        @Test
        @DisplayName("Deve lançar exceção quando tentar cancelar conta com saldo")
        void deveLancarExcecaoQuandoTentarCancelarContaComSaldo() {
            ContaCorrente cc = criarContaCorrente();
            ReflectionTestUtils.setField(cc, "saldo", BigDecimal.TEN);
            when(contaRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
            assertThrows(AccountHasBalanceException.class, () -> contaService.cancelarConta(cc.getId()));
            verify(contaRepository).findById(cc.getId());
        }
    }
}
