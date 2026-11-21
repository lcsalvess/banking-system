package com.lcsalvess.bankingsystem.controller.user;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDto;
import com.lcsalvess.bankingsystem.dto.request.user.UserRoleUpdateRequestDto;
import com.lcsalvess.bankingsystem.dto.response.user.CurrentUserResponseDto;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDto;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDto;
import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.service.user.UserService;
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
import org.springframework.data.domain.Sort;
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
 * REST controller responsible for user management and administration.
 */
@Tag(
    name = "Users",
    description = "User management and administration."
)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private static final String SORTABLE =
      "employeeId,fullName,username,email,role,active";

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of(SORTABLE.split(","));

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Retrieves a paginated list of users with optional search and filters.
   *
   * @param search optional search term for employee ID, full name, username, or email
   * @param role optional role filter
   * @param active optional account status filter
   * @param pageable pagination and sorting parameters
   * @return a page of user summary response DTOs
   */
  @Operation(
      summary = "List users",
      description =
          """
          Retrieves a paginated list of human users with optional filters.
          Search supports employee ID, full name, username and email.
          The default page size is 20, and the default sort order is
          ascending by employee ID. The maximum page size is 100.
          """
  )
  @SortableBy(
      value = SORTABLE,
      defaultSort = "employeeId,ASC"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Users retrieved successfully."
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid pagination, sorting or filter parameters."
      ),
      @ApiResponse(
          responseCode = "403",
          description = "The authenticated user does not have permission "
              + "to perform this operation."
      )
  })
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Page<UserSummaryResponseDto> findAll(
      @Parameter(
          description = "Search by employee ID, full name, username or email.",
          example = "john"
      )
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean active,
      @ParameterObject
      @PageableDefault(
          size = 20,
          sort = "employeeId",
          direction = Sort.Direction.ASC
      )
      Pageable pageable
  ) {
    SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

    return userService.findAll(search, role, active, pageable);
  }

  /**
   * Retrieves the profile of the currently authenticated user.
   *
   * @return the authenticated user's profile
   */
  @Operation(
      summary = "Get current user profile",
      description = "Retrieves the profile of the currently authenticated user."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Current user profile retrieved successfully."
      )
  })
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public CurrentUserResponseDto getCurrentUserProfile() {
    return userService.getCurrentUserProfile();
  }

  /**
   * Creates a new user account with an automatically generated employee ID.
   *
   * @param dto the user creation data
   * @return the created user details
   */
  @Operation(
      summary = "Create a user",
      description = "Creates a new human user account. The employee ID is "
          + "generated automatically. Only users with the ADMIN role "
          + "are authorized to perform this operation."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "User created successfully."
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request body or validation errors."
      ),
      @ApiResponse(
          responseCode = "409",
          description = "A user with the provided unique information already exists."
      )
  })
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponseDto create(@RequestBody @Valid UserRequestDto dto) {
    return userService.create(dto);
  }

  /**
   * Reactivates an existing inactive user account.
   *
   * @param employeeId the employee identifier assigned to the user
   * @return the reactivated user details
   */
  @Operation(
      summary = "Reactivate a user",
      description = "Reactivates an existing inactive user account using "
          + "their employee ID. Only users with the ADMIN role "
          + "are authorized to perform this operation."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "User reactivated successfully."
      ),
      @ApiResponse(
          responseCode = "403",
          description = "The authenticated user does not have permission."
      ),
      @ApiResponse(
          responseCode = "404",
          description = "User not found."
      )
  })
  @PatchMapping("/{employeeId}/reactivate")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponseDto reactivate(
      @Parameter(
          description = "Employee identifier assigned to the user.",
          example = "EMP-000004"
      )
      @PathVariable("employeeId") String employeeId
  ) {
    return userService.reactivate(employeeId);
  }

  /**
   * Deactivates an existing user account without deleting its data.
   *
   * @param employeeId the employee identifier assigned to the user
   * @return the deactivated user details
   */
  @Operation(
      summary = "Deactivate a user",
      description = "Deactivates an existing user without deleting their data. "
          + "The user is identified by their employee ID. Only users "
          + "with the ADMIN role are authorized to perform this operation."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "User deactivated successfully."
      ),
      @ApiResponse(
          responseCode = "403",
          description = "The authenticated user does not have permission "
              + "to perform this operation."
      ),
      @ApiResponse(
          responseCode = "404",
          description = "User not found."
      ),
      @ApiResponse(
          responseCode = "409",
          description = "The last active administrator cannot be deactivated."
      )
  })
  @PatchMapping("/{employeeId}/deactivate")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponseDto deactivate(
      @Parameter(
          description = "Employee identifier assigned to the user.",
          example = "EMP-000004"
      )
      @PathVariable("employeeId") String employeeId
  ) {
    return userService.deactivate(employeeId);
  }

  /**
   * Changes an existing user's role and revokes previously issued tokens.
   *
   * @param employeeId the employee identifier assigned to the user
   * @param dto the requested role update
   * @return the updated user details
   */
  @Operation(
      summary = "Change a user's role",
      description = "Changes the role of an existing user identified by "
          + "their employee ID and revokes previously issued tokens, "
          + "requiring reauthentication. Only users with the ADMIN role "
          + "are authorized to perform this operation. An administrator "
          + "cannot change their own role."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Role changed successfully, or the user already "
              + "had the requested role."
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request body."
      ),
      @ApiResponse(
          responseCode = "403",
          description = "The authenticated user does not have permission, "
              + "or is trying to change their own role."
      ),
      @ApiResponse(
          responseCode = "404",
          description = "User not found."
      ),
      @ApiResponse(
          responseCode = "409",
          description = "The requested role change would leave the system "
              + "without an active administrator."
      )
  })
  @PatchMapping("/{employeeId}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponseDto changeRole(
      @Parameter(
          description = "Employee identifier assigned to the user.",
          example = "EMP-000004"
      )
      @PathVariable("employeeId") String employeeId,
      @RequestBody @Valid UserRoleUpdateRequestDto dto
  ) {
    return userService.changeRole(employeeId, dto.role());
  }
}
