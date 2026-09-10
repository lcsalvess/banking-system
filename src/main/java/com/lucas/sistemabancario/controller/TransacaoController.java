package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.response.TransacaoResponseDTO;
import com.lucas.sistemabancario.service.TransacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/transacao")
public class TransacaoController {
    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @PostMapping("/deposito/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void depositar(@PathVariable Long contaId, @RequestParam BigDecimal valor) {
        transacaoService.depositar(contaId, valor);
    }

    @GetMapping("/conta/{contaId}")
    public List<TransacaoResponseDTO> listarPorConta(@PathVariable Long contaId) {
        return transacaoService.listarPorConta(contaId);
    }

    @PostMapping("/saque/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sacar(@PathVariable Long contaId, @RequestParam BigDecimal valor) {
        transacaoService.sacar(contaId, valor);
    }

    @PostMapping("/transferencia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferir(@RequestParam Long contaIdOrigem,
                           @RequestParam Long contaIdDestino,
                           @RequestParam BigDecimal valor) {
        transacaoService.transferir(contaIdOrigem, contaIdDestino, valor);
    }

    @PostMapping("/rendimento/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aplicarRendimento(@PathVariable Long contaId) {
        transacaoService.aplicarRendimento(contaId);
    }
}
