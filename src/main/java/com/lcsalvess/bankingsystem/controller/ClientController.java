package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.ClientRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.ClientSummaryResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@PreAuthorize("hasRole('EMPLOYEE')")
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
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "CPF already registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponseDTO create(@Valid @RequestBody ClientRequestDTO dto) {
        return clientService.create(dto);
    }

    @Operation(
            summary = "Get clients",
            description = "Retrieves clients using pagination and an optional name filter."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Clients retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ClientSummaryResponseDTO.class
                            )
                    )
            )
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<ClientSummaryResponseDTO> findAll(
            @Parameter(description = "Filter clients by name", example = "Lucas")
            @RequestParam(required = false) String name,
            @ParameterObject
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return clientService.findAll(name, pageable);
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
                    responseCode = "400",
                    description = "Invalid client ID format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ClientResponseDTO findById(
            @Parameter(
                    description = "Client unique identifier",
                    example = "1",
                    required = true
            )
            @PathVariable Long id) {
        return clientService.findById(id);
    }

    @Operation(
            summary = "Partially update client by ID",
            description = "Updates only the fields sent in the request. Omitted (null) fields are left unchanged."
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
                    description = "Invalid client data or request format",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                  implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    @PatchMapping("/{id}")
    public ClientResponseDTO update(
            @Parameter(
                    description = "Client unique identifier",
                    example = "1",
                    required = true
            )
            @PathVariable Long id, @Valid @RequestBody ClientUpdateRequestDTO dto) {
        return clientService.update(id, dto);
    }
}
