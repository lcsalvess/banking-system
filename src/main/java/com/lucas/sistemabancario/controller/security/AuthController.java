package com.lucas.sistemabancario.controller.security;

import com.lucas.sistemabancario.dto.request.security.LoginRequestDTO;
import com.lucas.sistemabancario.dto.response.security.LoginResponseDTO;
import com.lucas.sistemabancario.service.security.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO responseDTO = authService.authenticate(dto);
        return responseDTO;
    }
}
