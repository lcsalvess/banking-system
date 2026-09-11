package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.request.ContaRequestDTO;
import com.lucas.sistemabancario.dto.response.ContaResponseDTO;
import com.lucas.sistemabancario.service.ContaService;
import jakarta.validation.Valid;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponseDTO criar (@Valid @RequestBody ContaRequestDTO dto) {
        return contaService.criar(dto);
    }

    @PatchMapping("/cancelar/{contaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarConta(@PathVariable Long contaId) {
        contaService.cancelarConta(contaId);
    }
}
