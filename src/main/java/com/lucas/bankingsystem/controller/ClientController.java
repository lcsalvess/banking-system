package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.ClientRequestDTO;
import com.lucas.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lucas.bankingsystem.dto.response.ClientResponseDTO;
import com.lucas.bankingsystem.dto.response.ErrorResponse;
import com.lucas.bankingsystem.entity.Client;
import com.lucas.bankingsystem.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(
            summary = "Create a client",
            description = "Creates a new client."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Client created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClientResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client data or request format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "CPF already registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponseDTO create(@Valid @RequestBody ClientRequestDTO dto) {
        Client savedClient = clientService.save(dto);
        return ClientResponseDTO.fromEntity(savedClient);
    }

    @Operation(
            summary = "Get all clients",
            description = "Retrieves all registered clients."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Clients retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ClientResponseDTO.class)
                            )
                    )
            )
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ClientResponseDTO> findAll() {
        return clientService.findAll();
    }

    @Operation(
            summary = "Get client by ID",
            description = "Retrieves a client by its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client found successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClientResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ClientResponseDTO findById(
            @Parameter(
                    description = "Client unique identifier",
                    example = "1"
            )
            @PathVariable Long id) {
        return clientService.findById(id);
    }

    @Operation(
            summary = "Update client by ID",
            description = "Updates the information of an existing client."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClientResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ClientResponseDTO update(
            @Parameter(
                    description = "Client unique identifier",
                    example = "1"
            )
            @PathVariable Long id, @Valid @RequestBody ClientUpdateRequestDTO dto) {
        Client updatedClient = clientService.update(id, dto);
        return ClientResponseDTO.fromEntity(updatedClient);
    }
}
