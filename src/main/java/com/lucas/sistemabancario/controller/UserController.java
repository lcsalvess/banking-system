package com.lucas.sistemabancario.controller;

import com.lucas.sistemabancario.dto.request.UserRequestDTO;
import com.lucas.sistemabancario.dto.response.UserResponseDTO;
import com.lucas.sistemabancario.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@RequestBody @Valid UserRequestDTO dto) {
        return userService.create(dto);
    }
}
