package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.request.TransacaoRequestDTO;
import com.lucas.sistemabancario.dto.response.TransacaoResponseDTO;
import com.lucas.sistemabancario.service.TransacaoService;
import jakarta.validation.Valid;
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

    @PostMapping("/deposito")
    @ResponseStatus(HttpStatus.CREATED)
    public TransacaoResponseDTO depositar(@Valid @RequestBody TransacaoRequestDTO dto) {
        return transacaoService.depositar(dto);
    }

    @GetMapping("/conta/{contaId}")
    public List<TransacaoResponseDTO> listarPorConta(@PathVariable Long contaId) {
        return transacaoService.listarPorConta(contaId);
    }

    @PostMapping("/saque")
    @ResponseStatus(HttpStatus.CREATED)
    public TransacaoResponseDTO sacar(@Valid @RequestBody TransacaoRequestDTO dto) {
        return transacaoService.sacar(dto);
    }

    @PostMapping("/transferencia")
    @ResponseStatus(HttpStatus.CREATED)
    public TransacaoResponseDTO transferir(@Valid @RequestBody TransacaoRequestDTO dto) {
        return transacaoService.transferir(dto);
    }

    @PostMapping("/rendimento/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public TransacaoResponseDTO aplicarRendimento(@PathVariable Long contaId) {
        return transacaoService.aplicarRendimento(contaId);
    }
}
