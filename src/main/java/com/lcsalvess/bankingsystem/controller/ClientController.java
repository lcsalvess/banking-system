package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.client.ClientRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.client.ClientResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.client.ClientSummaryResponseDTO;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(
        name = "Clients",
        description = "Client registration and management."
)
@PreAuthorize("hasRole('EMPLOYEE')")
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private static final String SORTABLE = "id,name";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

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
                    description = "Client created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client data or request format"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "CPF already registered"
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponseDTO create(
            @Valid @RequestBody ClientRequestDTO dto
    ) {
        return clientService.create(dto);
    }

    @Operation(
            summary = "Get clients",
            description = """
                Retrieves clients using pagination and an optional name filter.
                The default page size is 10, and the default sort order is
                ascending by ID. The maximum page size is 100.
                """
    )
    @SortableBy(
            value = SORTABLE,
            defaultSort = "id,ASC"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Clients retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"
            )
    })
    @GetMapping
    public Page<ClientSummaryResponseDTO> findAll(
            @Parameter(description = "Filter clients by name", example = "Lucas")
            @RequestParam(required = false) String name,
            @ParameterObject
            @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return clientService.findAll(name, pageable);
    }

    @Operation(
            summary = "Get client by ID",
            description = "Retrieves a client by its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client found successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client ID format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found"
            )
    })
    @GetMapping("/{id}")
    public ClientResponseDTO findById(
            @Parameter(description = "Client unique identifier", example = "1")
            @PathVariable Long id
    ) {
        return clientService.findById(id);
    }

    @Operation(
            summary = "Partially update client by ID",
            description = "Only the provided client fields are updated; "
                    + "omitted or null fields remain unchanged. "
                    + "When an address is provided, it replaces the existing address entirely. "
                    + "If the complement is omitted or set to null, the existing complement is cleared."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Client updated successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid client data or request format"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email already registered"
            )
    })
    @PatchMapping("/{id}")
    public ClientResponseDTO update(
            @Parameter(description = "Client unique identifier", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ClientUpdateRequestDTO dto
    ) {
        return clientService.update(id, dto);
    }
}