package com.lcsalvess.bankingsystem.controller.client;

import com.lcsalvess.bankingsystem.dto.request.client.ClientRequestDto;
import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientResponseDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientSummaryResponseDto;
import com.lcsalvess.bankingsystem.service.client.ClientService;
import com.lcsalvess.bankingsystem.util.pagination.SortWhitelist;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for client registration, retrieval, and management.
 */
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

  /**
   * Creates a new client.
   *
   * @param dto the client registration data
   * @return the created client details
   */
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
          responseCode = "404",
          description = "Postal code not found."
      ),
      @ApiResponse(
          responseCode = "409",
          description = "CPF already registered"
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Address lookup provider or database is temporarily unavailable."
      )
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClientResponseDto create(
      @Valid @RequestBody ClientRequestDto dto
  ) {
    return clientService.create(dto);
  }

  /**
   * Retrieves clients using pagination and an optional name filter.
   *
   * @param name the optional client name filter
   * @param pageable pagination and sorting parameters
   * @return a page of client summary response DTOs
   */
  @Operation(
      summary = "Get clients",
      description =
          """
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
  public Page<ClientSummaryResponseDto> findAll(
      @Parameter(description = "Filter clients by name", example = "Lucas")
      @RequestParam(required = false) String name,
      @ParameterObject
      @PageableDefault(sort = "id") Pageable pageable
  ) {
    SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

    return clientService.findAll(name, pageable);
  }

  /**
   * Retrieves a client by its unique identifier.
   *
   * @param clientId the client's unique identifier
   * @return the client details
   */
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
  @GetMapping("/{clientId}")
  public ClientResponseDto findById(
      @Parameter(description = "Client unique identifier", example = "1")
      @PathVariable Long clientId
  ) {
    return clientService.findById(clientId);
  }

  /**
   * Partially updates a client using the provided request fields.
   * When an address is provided, it replaces the existing address entirely.
   * An omitted or null address complement is cleared.
   *
   * @param clientId the client's unique identifier
   * @param dto the client update data
   * @return the updated client details
   */
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
          description = "Client or postal code not found."
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Email already registered"
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Address lookup provider or database is temporarily unavailable."
      )
  })
  @PatchMapping("/{clientId}")
  public ClientResponseDto update(
      @Parameter(description = "Client unique identifier", example = "1")
      @PathVariable Long clientId,
      @Valid @RequestBody ClientUpdateRequestDto dto
  ) {
    return clientService.update(clientId, dto);
  }
}
