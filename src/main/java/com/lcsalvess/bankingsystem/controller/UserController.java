package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserResponseDTO;
import com.lcsalvess.bankingsystem.dto.response.user.UserSummaryResponseDTO;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(
        name = "Users",
        description = "User management and administration."
)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final String SORTABLE = "id,username,email,role,active";

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of(SORTABLE.split(","));

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "List users",
            description = """
                Retrieves a paginated list of users with optional filters.
                The default page size is 20, and the default sort order is
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
                    description = "Users retrieved successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination, sorting or filter parameters."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The authenticated user does not have permission to perform this operation."
            )
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserSummaryResponseDTO> findAll(
            @Parameter(
                    description = "Search by username or email",
                    example = "lucas"
            )
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "id",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, SORTABLE_PROPERTIES);

        return userService.findAll(search, role, active, pageable);
    }

    @Operation(
            summary = "Create a user",
            description = "Creates a new user account. Only users with the ADMIN role "
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
    public UserResponseDTO create(@RequestBody @Valid UserRequestDTO dto) {
        return userService.create(dto);
    }

    @Operation(
            summary = "Reactivate a user",
            description = "Reactivates an existing inactive user account. "
                    + "Only users with the ADMIN role are authorized "
                    + "to perform this operation."
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
    @PatchMapping("/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDTO reactivate(@PathVariable Long userId) {
        return userService.reactivate(userId);
    }

    @Operation(
            summary = "Deactivate a user",
            description = "Deactivates an existing user without deleting their data. "
                    + "Only users with the ADMIN role are authorized to perform this operation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User deactivated successfully."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The authenticated user does not have permission to perform this operation."
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
    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDTO deactivate(@PathVariable Long userId) {
        return userService.deactivate(userId);
    }
}