package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.ContaResponseDTO;
import com.lucas.sistemabancario.entity.Conta;
import com.lucas.sistemabancario.entity.ContaCorrente;
import com.lucas.sistemabancario.entity.ContaPoupanca;
import com.lucas.sistemabancario.service.ContaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contas")
public class ContaController {
    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    public List<ContaResponseDTO> listar() {
        return contaService.listar();
    }

    @GetMapping("/{id}")
    public ContaResponseDTO buscarPorId(@PathVariable Long id) {
        return contaService.buscarPorId(id);
    }

    @PostMapping("/corrente/{clienteId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaCorrente criarContaCorrente(@PathVariable Long clienteId) {
        return contaService.criarContaCorrente(clienteId);
    }

    @PostMapping("/poupanca/{clienteId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaPoupanca criarContaPoupanca(@PathVariable Long clienteId) {
        return contaService.criarContaPoupanca(clienteId);
    }

    @PatchMapping("/cancelar/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarConta(@PathVariable Long contaId) {
        contaService.cancelarConta(contaId);
    }
}
