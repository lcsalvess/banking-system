package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {
    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponseDTO create(@Valid @RequestBody ClientRequestDTO dto) {
        Client savedClient = clientService.save(dto);
        return ClientResponseDTO.fromEntity(savedClient);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ClientResponseDTO> findAll() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    public ClientResponseDTO findById(@PathVariable Long id) {
        return clientService.findById(id);
    }

    @PutMapping("/{id}")
    public ClientResponseDTO update(@PathVariable Long id, @Valid @RequestBody ClientUpdateRequestDTO dto) {
        Client updatedClient = clientService.update(id, dto);
        return ClientResponseDTO.fromEntity(updatedClient);
    }
}
