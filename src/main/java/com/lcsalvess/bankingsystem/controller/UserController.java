package com.lcsalvess.bankingsystem.controller;

import com.lcsalvess.bankingsystem.dto.request.UserRequestDTO;
import com.lcsalvess.bankingsystem.dto.response.UserResponseDTO;
import com.lcsalvess.bankingsystem.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}