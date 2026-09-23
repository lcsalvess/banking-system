package com.lucas.bankingsystem.controller;

import com.lucas.bankingsystem.dto.request.UserRequestDTO;
import com.lucas.bankingsystem.dto.response.UserResponseDTO;
import com.lucas.bankingsystem.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@RequestBody @Valid UserRequestDTO dto) {
        return userService.create(dto);
    }
}
