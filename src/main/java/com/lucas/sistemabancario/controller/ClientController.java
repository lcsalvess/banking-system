package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.request.ClientUpdateRequestDTO;
import com.lucas.sistemabancario.dto.response.ClientResponseDTO;
import com.lucas.sistemabancario.dto.request.ClientRequestDTO;
import com.lucas.sistemabancario.entity.Client;
import com.lucas.sistemabancario.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clients")
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        clientService.deleteById(id);
    }
}
