package com.lcsalvess.bankingsystem.service.client;

import com.lcsalvess.bankingsystem.dto.request.client.ClientRequestDto;
import com.lcsalvess.bankingsystem.dto.request.client.ClientUpdateRequestDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientResponseDto;
import com.lcsalvess.bankingsystem.dto.response.client.ClientSummaryResponseDto;
import com.lcsalvess.bankingsystem.entity.address.Address;
import com.lcsalvess.bankingsystem.entity.client.Client;
import com.lcsalvess.bankingsystem.exception.client.ClientCpfAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientEmailAlreadyExistsException;
import com.lcsalvess.bankingsystem.exception.client.ClientNotFoundException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.repository.ClientRepository;
import com.lcsalvess.bankingsystem.service.address.AddressData;
import com.lcsalvess.bankingsystem.service.address.AddressService;
import com.lcsalvess.bankingsystem.service.client.ClientUpdateData.AddressUpdateData;
import com.lcsalvess.bankingsystem.service.security.CurrentUserService;
import com.lcsalvess.bankingsystem.util.pagination.PageableTieBreaker;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides client management operations, including creation, retrieval,
 * and updates of client information.
 */
@Service
public class ClientService {

  private final ClientRepository clientRepository;

  private final ClientPersistenceService clientPersistenceService;

  private final AddressService addressService;

  private final CurrentUserService currentUserService;

  public ClientService(
      ClientRepository clientRepository,
      ClientPersistenceService clientPersistenceService,
      AddressService addressService,
      CurrentUserService currentUserService
  ) {
    this.clientRepository = clientRepository;
    this.clientPersistenceService = clientPersistenceService;
    this.addressService = addressService;
    this.currentUserService = currentUserService;
  }

  /**
   * Creates a client after checking CPF uniqueness and resolving the address.
   *
   * @param dto the data required to register the client
   * @return the response containing the created client details
   * @throws ClientCpfAlreadyExistsException if a client with the supplied CPF
   *                                          already exists
   * @throws ClientEmailAlreadyExistsException if the supplied email address
   *                                            is already registered
   */
  public ClientResponseDto create(ClientRequestDto dto) {
    if (clientRepository.existsByCpf(dto.cpf())) {
      throw new ClientCpfAlreadyExistsException(
          ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS
      );
    }

    Address address = addressService.createFromPostalCode(dto.address());

    Client client = new Client(
        dto.name(),
        dto.cpf(),
        dto.email(),
        dto.phoneNumber(),
        address
    );

    try {
      Client savedClient = clientPersistenceService.create(
          client,
          getCurrentUsername()
      );

      return ClientResponseDto.fromEntity(savedClient);
    } catch (DataIntegrityViolationException exception) {
      if (DatabaseConstraint.CLIENT_CPF_UNIQUE.isViolatedBy(exception)) {
        throw new ClientCpfAlreadyExistsException(
            ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,
            exception
        );
      }

      if (DatabaseConstraint.CLIENT_EMAIL_UNIQUE.isViolatedBy(exception)) {
        throw new ClientEmailAlreadyExistsException(
            ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS,
            exception
        );
      }

      throw exception;
    }
  }

  /**
   * Retrieves a paginated list of clients, optionally filtering by name.
   * <p>
   * Results are sorted using the requested configuration with an ID tie-breaker
   * to ensure deterministic pagination.
   *
   * @param name the optional name text used to filter clients; blank or
   *             {@code null} values return all clients
   * @param pageable the pagination and sorting configuration
   * @return a page of client summary responses
   */
  @Transactional(readOnly = true)
  public Page<ClientSummaryResponseDto> findAll(
      String name,
      Pageable pageable
  ) {
    Pageable sortedPageable = PageableTieBreaker.withIdTieBreaker(pageable);

    Page<Client> clients;

    if (name == null || name.isBlank()) {
      clients = clientRepository.findAll(sortedPageable);
    } else {
      clients = clientRepository.findByNameContainingIgnoreCase(
          name.trim(),
          sortedPageable
      );
    }

    return clients.map(ClientSummaryResponseDto::fromEntity);
  }

  /**
   * Retrieves a client entity by its identifier.
   *
   * @param id the identifier of the client to retrieve
   * @return the matching client entity
   * @throws ClientNotFoundException if no client exists with the supplied identifier
   */
  public Client findEntityById(Long id) {
    return clientRepository.findById(id)
        .orElseThrow(ClientNotFoundException::new);
  }

  /**
   * Retrieves a client by its identifier and converts it into a response DTO.
   *
   * @param id the identifier of the client to retrieve
   * @return the response containing the client details
   * @throws ClientNotFoundException if no client exists with the supplied identifier
   */
  @Transactional(readOnly = true)
  public ClientResponseDto findById(Long id) {
    Client client = findEntityById(id);
    return ClientResponseDto.fromEntity(client);
  }

  /**
   * Updates the personal, contact, and optional address information of a client.
   *
   * @param id the identifier of the client to update
   * @param dto the data containing the fields to update
   * @return the response containing the updated client details
   * @throws ClientNotFoundException if no client exists with the supplied identifier
   */
  public ClientResponseDto update(Long id, ClientUpdateRequestDto dto) {

    AddressUpdateData address = null;

    if (dto.address() != null) {
      AddressData addressData = addressService.findAddressByPostalCode(
          dto.address().postalCode()
      );

      address = new AddressUpdateData(
          addressData,
          dto.address().streetNumber(),
          dto.address().complement()
      );
    }

    Client updatedClient = clientPersistenceService.update(
        id,
        new ClientUpdateData(
            dto.name(),
            dto.email(),
            dto.phoneNumber(),
            address
        ),
        getCurrentUsername()
    );

    return ClientResponseDto.fromEntity(updatedClient);
  }

  private String getCurrentUsername() {
    return currentUserService.getUsername();
  }
}
