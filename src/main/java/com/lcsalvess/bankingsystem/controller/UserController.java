package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.user.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.request.user.UserRoleUpdateRequestDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

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

    @Operation(
            summary = "List users",
            description = """
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
                    description = "The authenticated user does not have permission to perform this operation."
            )
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserSummaryResponseDTO> findAll(
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
    public UserResponseDTO create(@RequestBody @Valid UserRequestDTO dto) {
        return userService.create(dto);
    }

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
    public UserResponseDTO reactivate(
            @Parameter(
                    description = "Employee identifier assigned to the user.",
                    example = "EMP-000004"
            )
            @PathVariable("employeeId") String employeeId
    ) {
        return userService.reactivate(employeeId);
    }

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
    @PatchMapping("/{employeeId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDTO deactivate(
            @Parameter(
                    description = "Employee identifier assigned to the user.",
                    example = "EMP-000004"
            )
            @PathVariable("employeeId") String employeeId
    ) {
        return userService.deactivate(employeeId);
    }


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
                    description = "Role changed successfully, or the user already had the requested role."
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
                    description = "The requested role change would leave the system without an active administrator."
            )
    })
    @PatchMapping("/{employeeId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponseDTO changeRole(
            @Parameter(
                    description = "Employee identifier assigned to the user.",
                    example = "EMP-000004"
            )
            @PathVariable("employeeId") String employeeId,
            @RequestBody @Valid UserRoleUpdateRequestDTO dto
    ) {
        return userService.changeRole(employeeId, dto.role());
    }
}
